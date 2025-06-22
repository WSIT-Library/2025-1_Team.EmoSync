package com.example.capstone.util;

import com.example.capstone.dto.NotificationDto;
import com.example.capstone.service.SseEmitterService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class AsyncTaskListener {
    @Autowired
    private SseEmitterService emitterService;

    @EventListener
    public void handleAsyncTaskCompletedEvent(AsyncTaskCompletedEvent event) {
        // 비동기 작업 완료 후 알림 처리
        log.info("비동기 작업이 완료되었습니다");
        NotificationDto result = event.getResult();
        emitterService.broadcast(result.getReceiver_id(), result);
    }
}
