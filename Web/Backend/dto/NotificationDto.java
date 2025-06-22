package com.example.capstone.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder(toBuilder = true)
@Getter
public class NotificationDto {
    private Long id;
    private String receiver_id; // 콘텐츠 생성자(알림 수신자)
    private String notification_msg; // 알림 내용
    private String related_url; // 결과 페이지 url(성공한 경우에만)
    private String is_success; // 콘텐츠 생성 성공 여부(성공/실패)
    private String failure_msg; // 생성 실패 원인(실패한 경우에만 사용)
    private LocalDateTime request_date; // 생성 요청 날짜
}
