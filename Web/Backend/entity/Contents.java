package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
@Getter
@Builder(toBuilder = true)
public class Contents {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 기본 ID

    @JoinColumn(name = "MemberId")
    @ManyToOne(fetch = FetchType.LAZY)
    private Member con_member; // 해당 콘텐츠 소유자
    
    @Column
    private String emotion; // 사용자의 표정 분석으로 얻은 감정값

    @Column
    private String emotionType; // 감정 타입(positive, negative, neutral)

    @Column
    private String emotionDetail; // 사용자의 표정 분석으로 얻은 감정값

    @Column
    private String emotionTypeDetail; // 감정 타입(positive, negative, neutral)
    
    // 사용자 업로드 이미지가 이름/경로로 분리된 이유는 
    // 생성 결과가 마음에 들지 않을 때 콘텐츠 재생성을 위함
    @Column
    private String userImgName; // 사용자가 업로드한 이미지 파일 이름
    
    @Column
    private String userVoiceName; // 사용자가 업로드한 음성 파일 이름
    
    @Column
    private String userVoicePath; // 사용자가 녹음한 음성 파일 경로
    
    @Column
    private String generatedImg; // AI로 생성된 이미지

    @Column
    private String generatedMusic; // AI로 생성된 음악

    @Column
    private String userImgPath; // 사용자가 업로드한 이미지 파일 경로

    @Column
    private String generatedImgPath; // AI로 생성된 이미지 파일 경로

    @Column
    private String generatedMusicPath; // AI로 생성된 음악 파일 경로

    @Column
    private LocalDateTime generatedDate; // 생성된 날짜
}
