package com.mugreparty.sprinky_server.dto;

// clase para las peticiones/respuestas HTTP (no expones clases internas).


public record CreateRoomResponse(String code, String hostToken, String joinUrl, String qrCode) {}
