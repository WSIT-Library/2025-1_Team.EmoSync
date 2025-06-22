package com.example.capstone.repository;

import com.example.capstone.entity.Contents;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ContentsRepository extends JpaRepository<Contents, Long> {

    // 특정 유저가 특정 날짜에 생성한 콘텐츠를 최신순으로 가져오기
    @Query("SELECT f FROM Contents f WHERE f.con_member.id =:userId AND f.generatedDate BETWEEN :startDate AND :endDate ORDER BY f.id DESC")
    Page<Contents> findContentsByUserIdAndDate(Pageable pageable, String userId, LocalDateTime startDate, LocalDateTime endDate);

    // 특정 유저가 생성한 콘텐츠를 최신순으로 가져오기
    @Query("SELECT f FROM Contents f WHERE f.con_member.id =:userId ORDER BY f.id DESC")
    List<Contents> findListByUserId(String userId);

    // 특정 유저가 특정 날짜에 생성한 콘텐츠를 최신순으로 가져오기
    @Query("SELECT f FROM Contents f WHERE f.con_member.id =:userId AND f.generatedDate BETWEEN :startDate AND :endDate ORDER BY f.id DESC")
    List<Contents> findListByUserIdAndDate(String userId, LocalDateTime startDate, LocalDateTime endDate);
}
