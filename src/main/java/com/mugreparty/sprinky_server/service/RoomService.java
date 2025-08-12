package com.mugreparty.sprinky_server.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mugreparty.sprinky_server.domain.Room.GameMode;
import com.mugreparty.sprinky_server.domain.GameState;
import com.mugreparty.sprinky_server.domain.Player;
import com.mugreparty.sprinky_server.domain.Room;
import com.mugreparty.sprinky_server.domain.Round;
import com.mugreparty.sprinky_server.dto.StateUpdate;

@Service // Logica de la sala
public class RoomService {

    private static final int MAX_PLAYERS = 8;

    // Duraciones (puedes ajustarlas)
    private static final long PROMPT_DURATION_MS = 5_000;      // 10s para probar
    
private static final List<String> PROMPTS = List.of(
    "Completa: Miyazaki desayuna ____",
    "Completa: La vida es como ____",
    "Completa: Mi color favorito es ____",
    "Completa: En la playa encuentro ____"
);


    private String getRandomPrompt() {
        return PROMPTS.get(random.nextInt(PROMPTS.size()));
    }

    private static final long SUBMITTING_DURATION_MS = 30_000;  // 30s para probar
    private static final long VOTING_DURATION_MS = 15_000; // 30s para FAST
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

    /**
     * Crea una nueva sala con un código aleatorio y un host.
     * @return Información de la sala creada
     */
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

    /**
     * Busca una sala por su código.
     * @param code Código de la sala
     * @return Sala encontrada o vacía si no existe
     */
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
        if (room.getFirstPlayerId() == null){
          room.setFirstPlayerId(playerId);
        }

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
        broadcast(room);
        return new Joined(playerId, token);
    }

    /**
     * Genera un código de sala aleatorio de 4 caracteres.
     * @return Código único de sala
     */
    public String nextCode() {
        char[] buf = new char[4];
        for (int i = 0; i < buf.length; i++) buf[i] = ALPH[random.nextInt(ALPH.length)];
        String code = new String(buf);
        return rooms.containsKey(code) ? nextCode() :code;
    }

    /**
     * Envía el estado de la sala a todos los suscriptores.
     * @param room Sala a enviar
     */
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

    /**
     * Inicia el juego por el host.
     * @param code Código de la sala
     * @param hostToken Token del host
     */
    public void startGame(String code, String hostToken) {
        var room = rooms.get(code);
        if(room == null) throw new IllegalArgumentException("ROOM_NOT_FOUND");
        
        var bound = hostTokens.get(hostToken);
        if(bound == null || !bound.startsWith(code + ":"))
          throw new IllegalArgumentException("HOST_TOKEN_INVALID");

       startGameInternal(room);
    }

    /**
     * Ejecuta un tick cada segundo para verificar deadlines y avanzar estados.
     */
    @Scheduled(fixedRate = 1_000)
    public void tick() {
        long now = System.currentTimeMillis();
        rooms.values().forEach(room -> {
            if (room.getDeadlineEpochMs() > 0 && now >= room.getDeadlineEpochMs()) {
                advance(room);
            }
        });
    }

    /**
     * Avanza el estado de la sala según las reglas del juego.
     * @param room Sala a avanzar
     */
    private void advance(Room room) {
        long now = System.currentTimeMillis();
        switch (room.getState()) {
          case PROMPT -> {
            room.setState(GameState.SUBMITTING);
            room.setDeadlineEpochMs(now + SUBMITTING_DURATION_MS);
            broadcast(room);
          }
          case SUBMITTING -> {
            room.setState(GameState.VOTING);
            // puntuación simple
            var round = room.getCurrentRound();
            if (round != null) {
              round.getAnswers().forEach((playerId, ans) -> {
                if (ans != null && !ans.isBlank()) {
                  var p = room.getPlayers().get(playerId);
                  if (p != null) p.setScore(p.getScore() + 100); // +100 por respuesta
                }
              });
            }
            if (room.getMode() == GameMode.FAST) {
              room.setDeadlineEpochMs(now + VOTING_DURATION_MS); // ⟵ auto-next
            } else {
              room.setDeadlineEpochMs(0);                          // ⟵ manual
            }
            broadcast(room);
          }
          case VOTING -> {
              room.setState(GameState.SCORING);

              var round = room.getCurrentRound();
              if (round != null && round.getVotes() != null) {
                  Map<String, Long> voteCounts = round.getVotes().values().stream()
                      .collect(java.util.stream.Collectors.groupingBy(v -> v, java.util.stream.Collectors.counting()));

                  int totalVotes = round.getVotes().size();
                  voteCounts.forEach((playerId, votes) -> {
                      Player p = room.getPlayers().get(playerId);
                      if (p != null) {
                          p.setScore(p.getScore() + votes.intValue() * 100);
                          // BONUS: Si recibe el 100% de los votos, suma 2 puntos extra
                          if (votes == totalVotes && totalVotes > 1) {
                              p.setScore(p.getScore() + 200); // Puedes ajustar el bonus aquí
                          }
                      }
                  });
              }

              if (room.getMode() == GameMode.FAST) {
                room.setDeadlineEpochMs(now + SCORING_DURATION_MS);
              } else {
                room.setDeadlineEpochMs(0);
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
    
    /**
     * Inicia la siguiente ronda, asumiendo que ya se ha validado el estado.
     * @param code Código de la sala
     * @param hostToken Token del host
     */
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
    
    /**
     * Avanza a la siguiente ronda, reiniciando el estado.
     * @param room Sala a avanzar
     */
    private void nextRound(Room room) {
        room.setRoundNo(room.getRoundNo() + 1);

        var round = Round.builder()
                .promptId("q" + room.getRoundNo())
                .promptText(getRandomPrompt())
                .build();
        room.setCurrentRound(round);
    }

    /**
     * Cambia el modo de juego de la sala.
     * @param code Código de la sala
     * @param hostToken Token del host
     * @param modeStr "FAST" o "MANUAL"
     */
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
    
    /**
     * Inicia el juego internamente, asumiendo que ya se han validado las condiciones.
     * @param room Sala a iniciar
     */
    private void startGameInternal(Room room) {
      if (room.getPlayers().size() < 2 ) throw new IllegalArgumentException("NEED_2_PLAYERS_MIN");
      room.setState(GameState.PROMPT);
      room.setRoundNo(room.getRoundNo() + 1);
      var round = Round.builder()
                    .promptId("q" + room.getRoundNo())
                    .promptText(getRandomPrompt())
                    .build();
      room.setCurrentRound(round);
      
      room.setDeadlineEpochMs(System.currentTimeMillis() + PROMPT_DURATION_MS);
      broadcast(room);
    }

    /**
     * Inicia el juego por cualquier jugador autorizado (host o primer jugador).
     * @param code Código de la sala
     * @param hostToken Token del host (opcional)
     * @param playerToken Token del jugador (opcional)
     */
    public void startGameByAnyAuthorized(String code, String hostToken, String playerToken) {
      var room = rooms.get(code);
      if (room == null) throw new IllegalArgumentException("ROOM_NOT_FOUND");

      // HOST
      if (hostToken != null && !hostToken.isBlank()) {
        var bound = hostTokens.get(hostToken);
        if (bound == null || !bound.startsWith(code + ":"))
          throw new IllegalArgumentException("HOST_TOKEN_INVALID");
        startGameInternal(room);
        return;
      }

      // FIRST PLAYER
      if (playerToken != null && !playerToken.isBlank()) {
        var bound = playerTokens.get(playerToken); // "code:playerId
        if (bound == null || !bound.startsWith(code + ":"))
            throw new IllegalArgumentException("PLAYER_TOKEN_INVALID");
        
        String playerId = bound.substring(code.length() + 1);
        if (room.getFirstPlayerId() == null || !room.getFirstPlayerId().equals(playerId))
          throw new IllegalArgumentException("ONLY_FIRST_PLAYER");

        startGameInternal(room);
        return;
      }
      throw new IllegalArgumentException("MISSING_TOKEN");
    }

    /**
     * Vota por otro jugador en la ronda actual.
     * @param code Código de la sala
     * @param playerToken Token del jugador que vota
     * @param votedPlayerId ID del jugador al que se vota
     */
    public void vote(String code, String playerToken, String votedPlayerId) {
      var room = rooms.get(code);
      if (room == null) throw new IllegalArgumentException("ROOM_NOT_FOUND");
      if (room.getState() != GameState.VOTING) throw new IllegalArgumentException("NOT_VOTING");

      var bound = playerTokens.get(playerToken); // "code:playerId"
      if (bound == null || !bound.startsWith(code + ":")) throw new IllegalArgumentException("PLAYER_TOKEN_INVALID");

      String playerId = bound.substring(code.length() + 1);

      if (room.getPlayers().size() > 2 && playerId.equals(votedPlayerId)) {
          throw new IllegalArgumentException("NO_SELF_VOTE");
      }

      // Guarda los votos en la ronda actual
      var round = room.getCurrentRound();
      if (round == null) throw new IllegalStateException("NO_ROUND");

      // Crea el mapa de votos si no existe
      if (round.getVotes() == null) {
          round.setVotes(new java.util.HashMap<>());
      }

      // Solo se permite un voto por jugador
      if (round.getVotes().containsKey(playerId)) return;

      round.getVotes().put(playerId, votedPlayerId);
      broadcast(room);

      // Si todos han votado, avanza automáticamente
      if (round.getVotes().size() >= room.getPlayers().size()) {
        advance(room);
      }
  }
}


