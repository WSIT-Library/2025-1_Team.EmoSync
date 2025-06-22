package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;

import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
@Getter
@DynamicInsert
@Builder(toBuilder = true)
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column
    private String receiver_id; // 콘텐츠 생성자(알림 수신자)

    @Column
    private String notification_msg; // 알림 내용

    @Column
    private String related_url; // 결과 페이지 url(성공한 경우에만)

    @Column
    private String is_success; // 콘텐츠 생성 성공 여부(성공/실패)

    @Column
    private String failure_msg; // 생성 실패 원인(실패한 경우에만 사용)

    @Column
    private LocalDateTime request_date; // 생성 요청 날짜
}
