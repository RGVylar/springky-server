package com.mugreparty.sprinky_server.domain;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Round {
    private String promptId;              // id de la pregunta (MVP: cadena fija o null)
    private String promptText;            // texto de la pregunta
    @Builder.Default
    private Map<String, String> answers = new LinkedHashMap<>(); // playerId -> answer
    @Builder.Default
    private Map<String, String> votes = new LinkedHashMap<>();   // playerId -> votedPlayerId
}