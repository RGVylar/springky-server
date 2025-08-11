package com.mugreparty.sprinky_server.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mugreparty.sprinky_server.domain.GameMode;
import com.mugreparty.sprinky_server.domain.GameState;
import com.mugreparty.sprinky_server.domain.Player;
import com.mugreparty.sprinky_server.domain.Room;
import com.mugreparty.sprinky_server.domain.Round;
import com.mugreparty.sprinky_server.dto.StateUpdate;

@Service // Logica de la sala
public class RoomService {

    private static final int MAX_PLAYERS = 8;

    // Duraciones (puedes ajustarlas)
    private static final long PROMPT_DURATION_MS = 10_000;      // 10s para probar
    private static final long SUBMITTING_DURATION_MS = 30_000;  // 30s para probar
    private static final long SCORING_DURATION_MS = 5_000; // 30s para FAST

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
        .mode(GameMode.FAST) // Por defecto
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

        var taken = room.getPlayers().values().stream()
            .anyMatch(p -> p.getNickname().equalsIgnoreCase(nickname));
        if (taken) throw new IllegalArgumentException("NICK_TAKEN");

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
        var prompt = (room.getCurrentRound() != null) ? room.getCurrentRound().getPromptText() : null;
        int answersCount = room.getCurrentRound() != null ? room.getCurrentRound().getAnswers().size() : 0;

        var view = new StateUpdate(
            room.getCode(),
            room.getState().name(),
            room.getRoundNo(),
            room.getDeadlineEpochMs(),
            room.getPlayers().values().stream()
                .map(p -> new StateUpdate.PlayerView(p.getId(), p.getNickname(), p.getScore(), p.isConnected()))
                .toList(),
            prompt,
            room.getMode().name(),
            answersCount
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

        room.setState((GameState.PROMPT));
        room.setRoundNo(room.getRoundNo() + 1 );

        var round = Round.builder()
                .promptId("q"+room.getRoundNo())
                .promptText("Completa: Miyazaki desayuna _____.")
                .build();
        room.setCurrentRound(round);

        long now = System.currentTimeMillis();
        room.setDeadlineEpochMs(System.currentTimeMillis() + 30_000); //30 segundos

        broadcast(room);
    }

    @Scheduled(fixedRate = 1_000)
    public void tick() {
        long now = System.currentTimeMillis();
        rooms.values().forEach(room -> {
            if (room.getDeadlineEpochMs() > 0 && now >= room.getDeadlineEpochMs()) {
                advance(room);
            }
        });
    }

    private void advance(Room room) {
        long now = System.currentTimeMillis();
        switch (room.getState()) {
          case PROMPT -> {
            room.setState(GameState.SUBMITTING);
            room.setDeadlineEpochMs(now + SUBMITTING_DURATION_MS);
            broadcast(room);
          }
          case SUBMITTING -> {
            room.setState(GameState.SCORING);
            // puntuación simple
            var round = room.getCurrentRound();
            if (round != null) {
              round.getAnswers().forEach((playerId, ans) -> {
                if (ans != null && !ans.isBlank()) {
                  var p = room.getPlayers().get(playerId);
                  if (p != null) p.setScore(p.getScore() + 1);
                }
              });
            }
            if (room.getMode() == GameMode.FAST) {
              room.setDeadlineEpochMs(now + SCORING_DURATION_MS); // ⟵ auto-next
            } else {
              room.setDeadlineEpochMs(0);                          // ⟵ manual
            }
            broadcast(room);
          }
          case SCORING -> {
            if (room.getMode() == GameMode.FAST) {
              // auto pasar a la siguiente ronda
              nextRound(room);
              room.setState(GameState.PROMPT);
              room.setDeadlineEpochMs(now + PROMPT_DURATION_MS);
              broadcast(room);
            } else {
              // MANUAL: no hacer nada; espera a /next
            }
          }
          default -> { /* LOBBY/END: nada */ }
        }
      }      

    public void submitAnswer(String code, String playerToken, String answer) {
        var room = rooms.get(code);
        if (room == null) throw new IllegalArgumentException("ROOM_NOT_FOUND");
        if (room.getState() != GameState.SUBMITTING) throw new IllegalArgumentException("NOT_SUBMITTING");
    
        var bound = playerTokens.get(playerToken); // "code:playerId"
        if (bound == null || !bound.startsWith(code + ":")) throw new IllegalArgumentException("PLAYER_TOKEN_INVALID");
    
        String playerId = bound.substring(code.length() + 1);
    
        var round = room.getCurrentRound();
        if (round == null) throw new IllegalStateException("NO_ROUND");
    
        // ✅ aceptar solo la primera
        if (round.getAnswers().containsKey(playerId)) return; // Si lo quito se puede sobreescribir
    
        round.getAnswers().put(playerId, answer.trim());
        broadcast(room);
    }
    
    public void startNextRound(String code, String hostToken) {
        var room = rooms.get(code);
        if (room == null) throw new IllegalArgumentException("ROOM_NOT_FOUND");
    
        // Validar hostToken con el MAPA que ya usas: hostToken -> "code:hostId"
        var bound = hostTokens.get(hostToken);
        if (bound == null || !bound.startsWith(code + ":"))
            throw new IllegalArgumentException("HOST_TOKEN_INVALID");
    
        if (room.getState() != GameState.SCORING)
            throw new IllegalStateException("INVALID_STATE"); // solo dejamos avanzar desde SCORING
    
        // Crear NUEVA ronda (sube roundNo y limpia respuestas)
        nextRound(room);
    
        // Pasar a PROMPT y poner deadline de PROMPT
        room.setState(GameState.PROMPT);
        room.setDeadlineEpochMs(System.currentTimeMillis() + PROMPT_DURATION_MS);
    
        broadcast(room);
    }
    
    // Crea una nueva ronda en la sala (helper privado del servicio)
    private void nextRound(Room room) {
        room.setRoundNo(room.getRoundNo() + 1);
    
        var round = Round.builder()
                .promptId("q" + room.getRoundNo())
                .promptText("Completa: Miyazaki desayuna _____.") // luego lo cambiaremos por un generador real o una lista
                .build();
    
        room.setCurrentRound(round);
    }

    public void setMode(String code, String hostToken, String modeStr) {
        var room = rooms.get(code);
        if (room == null) throw new IllegalArgumentException("ROOM_NOT_FOUND");
        var bound = hostTokens.get(hostToken);
        if (bound == null || !bound.startsWith(code + ":")) throw new IllegalArgumentException("HOST_TOKEN_INVALID");
      
        var mode = GameMode.valueOf(modeStr.toUpperCase()); // "FAST" o "MANUAL"
        room.setMode(mode);
      
        // si está en SCORING y pasas a FAST, programa auto-next
        if (room.getState() == GameState.SCORING && room.getDeadlineEpochMs() == 0 && mode == GameMode.FAST) {
          room.setDeadlineEpochMs(System.currentTimeMillis() + SCORING_DURATION_MS);
        }
        broadcast(room);
    }      

}
