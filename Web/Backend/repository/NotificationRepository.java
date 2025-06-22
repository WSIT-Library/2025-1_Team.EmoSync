package com.example.capstone.repository;

import com.example.capstone.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // 특정 사용자의 전체 알림 내역 가져오기
    @Query("SELECT n FROM Notification n WHERE n.receiver_id = :receiver ORDER BY n.id DESC")
    Page<Notification> findAllDesc(String receiver, Pageable pageable);
}
