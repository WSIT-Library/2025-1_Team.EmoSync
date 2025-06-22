package com.example.capstone.util;

import com.example.capstone.entity.Member;
import com.example.capstone.service.EmailService;
import com.example.capstone.service.MemberService;
import com.example.capstone.service.NotificationService;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class AutoExecute {
    // 버킷 이름
    @Value("${spring.cloud.gcp.storage.bucket}")
    private String bucketName;

    private final MemberService memberService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final Storage storage;

    public AutoExecute(MemberService memberService, EmailService emailService, NotificationService notificationService,Storage storage) {
        this.memberService = memberService;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.storage = storage;
    }

    // 일정 주기마다 자동으로 Temp 디렉터리에 있는 파일 삭제
    //@Scheduled(fixedDelay = 300000) //테스트용(300초 마다 실행)
    @Scheduled(cron = "0 10 0 * * *") //실제 서비스용(매일 0시 10분에 실행)
    public void TempDel() {
        Bucket bucket = storage.get(bucketName);
        // 특정 경로에 있는 파일 리스트 가져오기
        Iterable<Blob> blobs = bucket.list(Storage.BlobListOption.prefix("upload/inquiry/TempImage/")).iterateAll();

        boolean hasFiles = false;

        for (Blob blob : blobs) {
            // 파일 경로가 "폴더/"로 끝나는지 확인하여, 폴더는 삭제하지 않음
            if (!blob.getName().endsWith("/")) {
                System.out.println("Deleting file: " + blob.getName());
                blob.delete();  // 파일 삭제
                hasFiles = true;
            }
        }

        if (!hasFiles) {
            System.out.println("폴더에 파일이 없습니다.");
        }

        System.out.println("모든 파일 삭제 완료.");
    }

    // 일정 주기마다 자동으로 free plan 을 이용하는 유저의 잔여 횟수를 10으로 초기화시킴
    //@Scheduled(fixedDelay = 300000) //테스트용(300초 마다 실행)
    @Scheduled(cron = "0 0 0 * * *") //실제 서비스용(매일 0시 0분에 실행)
    public void free_AutoInit(){
        int update_row = memberService.free_AutoInit();
        if(update_row == 0){
            System.out.println("[AutoExecute] ROLE_USER 가 없거나 free plan 을 이용하는 유저가 없음.");
        }
        else {
            System.out.println("[AutoExecute] free plan 이용자 "+ update_row + "명의 잔여 횟수 초기화");
        }
    }

    // 콘텐츠 생성 알림 목록을 초기화
    //@Scheduled(fixedDelay = 300000) //테스트용(300초 마다 실행)
    @Scheduled(cron = "0 30 0 * * *") //실제 서비스용(매일 0시 30분에 실행)
    public void notification_AutoDel(){
        notificationService.deleteAll_notification();
    }

    
    // 일정 주기마다 자동으로 premium 요금제를 사용하는 유저의 만료일이 일주일 남은 경우 이메일로 알림
    //@Scheduled(fixedDelay = 300000) //테스트용(300초 마다 실행)
    @Scheduled(cron = "0 0 9 * * *") //실제 서비스용(매일 9시 0분에 실행)
    public void sendExpiryNotifications_Auto() throws MessagingException {
        // 만료일이 일주일 남은 유저 리스트
        List<Member> memberList = memberService.find_ExpiryMember();
        for (Member member : memberList) {
            emailService.sendExpiryNotifications(member.getId(), member.getEmail(), member.getSubscription_end_date());
        }
    }
}
