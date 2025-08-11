package com.mugreparty.sprinky_server.domain;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Builder; // Deja construir Room de forma clara
import lombok.Data; // Genera getters/Setters/toString/equals/hasCode

@Data @Builder
public class Room {
    private String code;
    private String hostId;
    private GameState state;
    private int roundNo;
    private long deadlineEpochMs;
    private Round currentRound;
    private GameMode mode;

    @Builder.Default
    private Map<String, Player> players = new LinkedHashMap<>();
        public enum GameMode {
        FAST, MANUAL
            }

    }



