package com.mugreparty.sprinky_server.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mugreparty.sprinky_server.dto.CreateRoomResponse;
import com.mugreparty.sprinky_server.dto.JoinRoomRequest;
import com.mugreparty.sprinky_server.dto.JoinRoomResponse;
import com.mugreparty.sprinky_server.dto.SubmitAnswerRequest;
import com.mugreparty.sprinky_server.service.RoomService;
import com.mugreparty.sprinky_server.util.QrUtil;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.ui.Model;


@RestController
@RequestMapping("/rooms")
public class RoomController {
    
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<CreateRoomResponse> createRoom() {
        var created = roomService.createRoom();
        String joinUrl = "http://localhost:8080/rooms/" + created.code();
        try {
            String qrCodeBase64 = QrUtil.generateQrBase64(joinUrl);
            return ResponseEntity.ok(new CreateRoomResponse(
                created.code(),
                created.hostToken(),
                joinUrl,
                qrCodeBase64
            ));
        } catch (Exception e) {
            throw new RuntimeException("Error generando QR", e);
        }
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<JoinRoomResponse> joinRoom(
        @PathVariable String code,
        @RequestBody JoinRoomRequest request) {
            var joined = roomService.join(code, request.nickname());
            return ResponseEntity.ok(new JoinRoomResponse(joined.playerId(), joined.playerToken()));
        }
    
    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> getRoom(@PathVariable String code) {
        return roomService.find(code)
            .map(r -> {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("code", r.getCode());
                map.put("state", r.getState() != null ? r.getState().name() : null);
                map.put("roundNo", r.getRoundNo());
                map.put("promptText", 
                    r.getCurrentRound() != null ? r.getCurrentRound().getPromptText() : null);
                map.put("players",
                    r.getPlayers() != null
                        ? r.getPlayers().values().stream()
                            .map(p -> Map.of(
                                "id", p.getId(),
                                "nick", p.getNickname(),
                                "score", p.getScore()
                            ))
                            .toList()
                        : java.util.List.of()
                );
                return ResponseEntity.ok(map);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

   @PostMapping("/{code}/start")
    public ResponseEntity<Void> start(
        @PathVariable String code,
        @RequestHeader(value = "X-Host-Token", required = false) String hostToken,
        @RequestHeader(value = "X-Player-Token", required = false) String playerToken
    ) {
        roomService.startGameByAnyAuthorized(code, hostToken, playerToken);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{code}/answer")
    public ResponseEntity<Void> submitAnswer(@PathVariable String code,
        @RequestHeader("X-Player-Token") String playerToken,
        @RequestBody @jakarta.validation.Valid SubmitAnswerRequest req) {
        roomService.submitAnswer(code, playerToken, req.answer());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{code}/next")
    public ResponseEntity<Void> nextRound(
            @PathVariable String code,
            @RequestHeader("X-Host-Token") String hostToken) {
        roomService.startNextRound(code, hostToken);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{code}/mode/{mode}")
    public ResponseEntity<Void> setMode(@PathVariable String code,
                                        @PathVariable String mode,
                                        @RequestHeader("X-Host-Token") String hostToken) {
    roomService.setMode(code, hostToken, mode);
    return ResponseEntity.accepted().build();
    }

}
