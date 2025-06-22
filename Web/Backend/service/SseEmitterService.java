package com.example.capstone.service;

import com.example.capstone.dto.NotificationDto;
import com.example.capstone.repository.EmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseEmitterService {
    @Autowired
    private NotificationService notificationService;

    // SSE 이벤트 타임아웃 시간(30분)
    private static final Long DEFAULT_TIMEOUT = 1_800_000L;

    private final EmitterRepository emitterRepository;

    /**
     * 클라이언트의 이벤트 구독을 허용하는 메서드
     */
    public SseEmitter subscribe(String userId) {

        // 이전에 subscribe 했는지 여부 검사
        boolean subscribe_check = emitterRepository.existById(userId);
        // 이전에 동일한 userId 로 subscribe 했던 기록이 있는 경우
        if(subscribe_check) {
            // 이전 기록 삭제
            emitterRepository.deleteById(userId);
        }

        log.info("SseEmitterService - subscribe() 진입, userId={}", userId);
        // sse의 유효 시간이 만료되면, 클라이언트에서 다시 서버로 이벤트 구독을 시도한다.
        SseEmitter sseEmitter = emitterRepository.save(userId, new SseEmitter(DEFAULT_TIMEOUT));

        // 사용자에게 모든 데이터가 전송되었다면 emitter 삭제
        sseEmitter.onCompletion(() -> emitterRepository.deleteById(userId));

        // Emitter의 유효 시간이 만료되면 emitter 삭제
        // 유효 시간이 만료되었다는 것은 클라이언트와 서버가 연결된 시간동안 아무런 이벤트가 발생하지 않은 것을 의미한다.
        sseEmitter.onTimeout(() -> emitterRepository.deleteById(userId));

        // 첫 구독시에 이벤트를 발생시킨다.
        // sse 연결이 이루어진 후, 하나의 데이터로 전송되지 않는다면 sse의 유효 시간이 만료되고 503 에러가 발생한다.
        NotificationDto notificationDto = NotificationDto.builder()
                .receiver_id("sse_init_msg")
                .notification_msg("SSE 초기 설정")
                .build();
        sendToClient(userId, notificationDto);

        return sseEmitter;
    }

    /**
     * 이벤트가 구독되어 있는 클라이언트에게 데이터를 전송
     */
    public void broadcast(String receiver_id, NotificationDto notificationDto) {
        /* 여기에 알림 내용을 DB에 저장하는 코드 작성*/
        String result = notificationService.save_notification(notificationDto);

        // 알림이 DB 에 정상적으로 저장 됐다면 알림 보내기
        if(result.equals("success")){
            sendToClient(receiver_id, notificationDto);
        }
    }

    private void sendToClient(String userId, Object data) {
        log.info("SseEmitterService - sendToClient() 진입");
        log.info("userId={}", userId);
        SseEmitter sseEmitter = emitterRepository.findById(userId);
        log.info("sseEmitter : {}", sseEmitter);
        log.info("data={}", data);
        // 알림을 받을 사용자가 접속 중인 상태에서만 알림을 전송(DB 에는 따로 저장함)
        if(sseEmitter != null) {
            try {
                sseEmitter.send(
                        SseEmitter.event()
                                .name(userId)
                                .data(data)
                );
            } catch (Exception ex) {
                emitterRepository.deleteById(userId);
                log.info(ex.getMessage());
                throw new RuntimeException("연결 오류 발생");
            }
        }
    }
}
