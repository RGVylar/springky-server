package com.mugreparty.sprinky_server.dto;
import jakarta.validation.constraints.NotBlank;

public record SubmitAnswerRequest(@NotBlank String answer) {}
