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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;


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
        return ResponseEntity.ok(new CreateRoomResponse(created.code(), created.hostToken()));
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
            .map(r -> ResponseEntity.ok(Map.of(
                "code", r.getCode(),
                "state", r.getState().name(),
                "roundNo", r.getRoundNo(),
                "promptText", r.getCurrentRound() != null ? r.getCurrentRound().getPromptText() : null,    
                "players", r.getPlayers().values().stream()
                    .map(p -> Map.of("id", p.getId(), "nick", p.getNickname(), "score", p.getScore()))
                    .toList()
            )))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{code}/start")
    public ResponseEntity<Void> start(
        @PathVariable String code,
        @RequestHeader("X-Host-Token") String hostToken) {
        roomService.startGame(code, hostToken);
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
