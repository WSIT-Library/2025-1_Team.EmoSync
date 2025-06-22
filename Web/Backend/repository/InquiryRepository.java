package com.example.capstone.repository;

import com.example.capstone.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    // 전체 문의글 조회 후 최근 작성 순으로 가져오기
    @Query("SELECT f FROM Inquiry f ORDER BY f.id DESC")
    Page<Inquiry> findAllDesc(Pageable pageable);

    // 조회수 증가
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Inquiry SET views = :viewsAdd WHERE id = :inquiry_id")
    void updateViewsCount(@Param("viewsAdd") Integer viewsAdd, @Param("inquiry_id") Long inquiry_id);

    // 답변 여부 변경
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Inquiry SET respond = :respond_ck WHERE id = :inquiry_id")
    int updateRespondCk(@Param("respond_ck") String respond_ck, @Param("inquiry_id") Long inquiry_id);

    // 특정 문의글의 답변 여부 가져오기
    @Query("SELECT f.respond FROM Inquiry f WHERE f.id = :inquiry_id")
    String findRespondById(@Param("inquiry_id") Long inquiry_id);
}
