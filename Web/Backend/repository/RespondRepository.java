package com.example.capstone.repository;

import com.example.capstone.entity.Respond;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RespondRepository extends JpaRepository<Respond, Long> {
    // 특정 게시글에 작성된 댓글들 모두 조회하기
    @Query("select f from Respond f where f.inquiry.id = :inquiryId")
    List<Respond> findRespondByInquiryId(Long inquiryId);
}
