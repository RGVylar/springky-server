package com.mugreparty.sprinky_server.domain;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class Player {
    private String id;
    private String nickname;
    private int score;
    private boolean connected;
    private long lastSeenEpochMs;
}
