package com.example.capstone.service;

import com.example.capstone.dto.NotificationDto;
import com.example.capstone.entity.Notification;
import com.example.capstone.repository.MemberRepository;
import com.example.capstone.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class NotificationService {
    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberRepository memberRepository;

    // 특정 사용자의 전체 알림 내역 가져오기
    @Transactional(readOnly = true)
    public Page<Notification> All_notification(Pageable pageable, String receiver_id) {
        // page 번호 작업. 이 코드를 넣어야 주소에 page=1을 입력하면 첫번째 페이지로 이동함
        int page = (pageable.getPageNumber() == 0) ? 0:(pageable.getPageNumber() - 1); // page 는 index 처럼 0부터 시작
        // page 에 보여줄 게시글 개수 지정
        pageable = PageRequest.of(page,5);

        return notificationRepository.findAllDesc(receiver_id, pageable);
    }

    // 생성 시작을 알림에 저장
    public NotificationDto start_gen_notification(NotificationDto dto) {
        // 알림을 수신할 유저가 존재하는지 검사(존재하면 true, 없으면 false)
        boolean is_user = memberRepository.existsById(dto.getReceiver_id());

        // 알림을 수신할 유저가 DB 에 존재하는 경우에만 저장
        if (is_user) {
            // dto -> entity 변환
            Notification entity = Notification.builder()
                    .receiver_id(dto.getReceiver_id())
                    .notification_msg(dto.getNotification_msg())
                    .related_url(dto.getRelated_url())
                    .is_success(dto.getIs_success())
                    .failure_msg(dto.getFailure_msg())
                    .request_date(dto.getRequest_date())
                    .build();
            // DB 에 저장
            Notification result = notificationRepository.save(entity);
            return build_dto(result);
        }
        else{
            return null;
        }
    }

    // 알림을 DB 에 저장
    public String save_notification(NotificationDto dto) {
        // 임시 저장된 알림을 가져옴
        Notification temp_notification = notificationRepository.findById(dto.getId()).orElse(null);

        // 임시 저장된 알림이 DB 에 존재하는 경우에만 저장
        if(temp_notification != null) {
            // dto -> entity 변환
            Notification entity = temp_notification.toBuilder()
                    .receiver_id(dto.getReceiver_id())
                    .notification_msg(dto.getNotification_msg())
                    .related_url(dto.getRelated_url())
                    .is_success(dto.getIs_success())
                    .failure_msg(dto.getFailure_msg())
                    .request_date(dto.getRequest_date())
                    .build();
            // DB 에 저장
            notificationRepository.save(entity);

            return "success";
        }
        else {
            return "fail";
        }
    }

    // entity -> dto 변환
    public NotificationDto build_dto(Notification entity){
        return NotificationDto.builder()
                .id(entity.getId())
                .receiver_id(entity.getReceiver_id())
                .notification_msg(entity.getNotification_msg())
                .related_url(entity.getRelated_url())
                .is_success(entity.getIs_success())
                .failure_msg(entity.getFailure_msg())
                .request_date(entity.getRequest_date())
                .build();
    }

    // 알림 삭제 처리
    public String delete_notification(Long notification_id) {
        log.info("service 진입, notification_id: {}", notification_id);
        if(notificationRepository.existsById(notification_id)) {
            notificationRepository.deleteById(notification_id);
            log.info("notification_id: {} 삭제 완료", notification_id);
            return "Success";
        }
        return "존재하지 않는 알림 ID";
    }

    // 알림 전체 삭제 처리
    public void deleteAll_notification() {
        notificationRepository.deleteAll();
    }
}
