package com.mugreparty.sprinky_server.dto;

import java.util.List;

public record StateUpdate (
    String code,
    String state,
    int roundNo,
    long deadlineEpochMs,
    List<PlayerView> players
) {
    public record PlayerView(String id, String nickname, int score, boolean connected) {}
}
    

