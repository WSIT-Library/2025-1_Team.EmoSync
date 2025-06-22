package com.example.capstone.controller;

import com.example.capstone.dto.CustomUserDetails;
import com.example.capstone.dto.NotificationDto;
import com.example.capstone.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sse")
@Slf4j
public class SseController {
    private final SseEmitterService sseEmitterService;

    /**
     * 클라이언트의 이벤트 구독을 수락한다. text/event-stream은 SSE를 위한 Mime Type이다. 서버 -> 클라이언트로 이벤트를 보낼 수 있게된다.
     */
    @GetMapping(value = "/subscribe", produces = "text/event-stream")
    public SseEmitter subscribe(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
        log.info("SseController - subscribe() 진입");
        return sseEmitterService.subscribe(customUserDetails.getUsername());
    }

    /**
     * 이벤트를 구독 중인 클라이언트에게 데이터를 전송한다.
     */
    @PostMapping("/broadcast/{receiver}")
    public void broadcast(@PathVariable String receiver, @RequestBody NotificationDto notificationDto) {
        sseEmitterService.broadcast(receiver, notificationDto);
    }
}
