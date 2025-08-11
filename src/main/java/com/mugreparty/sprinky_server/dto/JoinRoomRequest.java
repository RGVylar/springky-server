package com.mugreparty.sprinky_server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinRoomRequest(
    @NotBlank @Size(max = 16) String nickname
) {}
    

