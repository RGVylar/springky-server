package com.mugreparty.sprinky_server.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.mugreparty.sprinky_server.domain.GameState;
import com.mugreparty.sprinky_server.domain.Player;
import com.mugreparty.sprinky_server.domain.Room;
import com.mugreparty.sprinky_server.dto.StateUpdate;

@Service // Logica de la sala
public class RoomService {

    private static final int MAX_PLAYERS = 8;
    private final SimpMessagingTemplate ws;
    public RoomService(SimpMessagingTemplate ws) { this.ws = ws; }

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> hostTokens = new ConcurrentHashMap<>();
    private final Map<String, String> playerTokens = new ConcurrentHashMap<>();

    private final SecureRandom random = new SecureRandom();
    private static final char[] ALPH = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    public record Created(String code, String hostToken) {}
    public record Joined(String playerId, String playerToken) {}

    public Created createRoom() {
        String code = nextCode();
        String hostId = UUID.randomUUID().toString();

        var room = Room.builder()
        .code(code)
        .hostId(hostId)
        .state(GameState.LOBBY)
        .roundNo(0)
        .deadlineEpochMs(0)
        .build();

        rooms.put(code, room);

        String hostToken = UUID.randomUUID().toString();
        hostTokens.put(hostToken, code + ":" + hostId);
        broadcast(room);
        return new Created(code, hostToken);
    }

    public Optional<Room> find(String code) {        
        return Optional.ofNullable(rooms.get(code));
    }

    public Joined join(String code, String nickname) {
        var room = rooms.get(code);
        if (room == null) throw new IllegalArgumentException("ROOM_NOT_FOUND");
        if (room.getPlayers().size() >= MAX_PLAYERS) throw new IllegalArgumentException("ROOM_FULL");

        String playerId = UUID.randomUUID().toString();
        var p = Player.builder()
                .id(playerId)
                .nickname(nickname)
                .score(0)
                .connected(true)
                .lastSeenEpochMs(Instant.now().toEpochMilli())
                .build();

        room.getPlayers().put(playerId, p);

        String token = UUID.randomUUID().toString();
        playerTokens.put(token, code + ":" + playerId);

        return new Joined(playerId, token);
    }

    public String nextCode() {
        char[] buf = new char[4];
        for (int i = 0; i < buf.length; i++) buf[i] = ALPH[random.nextInt(ALPH.length)];
        String code = new String(buf);
        return rooms.containsKey(code) ? nextCode() :code;
    }

    private void broadcast(Room room) {
        var view = new StateUpdate(
            room.getCode(),
            room.getState().name(),
            room.getRoundNo(),
            room.getDeadlineEpochMs(),
            room.getPlayers().values().stream()
                .map(p -> new StateUpdate.PlayerView(p.getId(), p.getNickname(), p.getScore(), p.isConnected()))
                .toList()
        );           
        ws.convertAndSend("/topic/rooms/" + room.getCode(), view); 
    }

    public void startGame(String code, String hostToken) {
        var room = rooms.get(code);
        if(room == null) throw new IllegalArgumentException("ROOM_NOT_FOUND");
        
        var bound = hostTokens.get(hostToken);
        if(bound == null || !bound.startsWith(code + ":"))
          throw new IllegalArgumentException("HOST_TOKEN_INVALID");

        if(room.getPlayers().size() < 2) throw new IllegalArgumentException("NEED_2_PLAYERS_MIN");

        room.setState((GameState.PROMT));
        room.setRoundNo(room.getRoundNo() + 1 );
        room.setDeadlineEpochMs(System.currentTimeMillis() + 30_000); //30 segundos

        broadcast(room);
    }
}
