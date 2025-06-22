package com.example.capstone.repository;

import com.example.capstone.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MemberRepository extends JpaRepository<Member, String> {
    // 이메일이 DB 에 존재하는가?
    boolean existsByEmail(String email);

    boolean existsByIdOrEmail(String id, String email);

    // 특정 아이디를 가진 회원의 비밀번호를 검색
    @Query("select f.password from Member f where f.id = ?1")
    String findPasswordById(String id);

    // 특정 아이디를 가진 회원의 이메일을 검색
    @Query("select f.email from Member f where f.id = ?1")
    String findEmailById(String id);

    // 특정 이메일을 가진 회원의 아이디를 검색
    @Query("select f.id from Member f where f.email = ?1")
    String findIdByEmail(String email);

    // 특정 아이디를 가진 회원의 권한을 검색
    @Query("select f.role from Member f where f.id = ?1")
    String findRoleById(String id);
    
    // 특정 아이디를 가진 회원의 일일 콘텐츠 생성 가능 횟수 검색
    @Query("select f.contents_count from Member f where f.id = ?1")
    Integer findContentsCount(String id);

    // free plan 을 이용하는 유저의 잔여 횟수를 10으로 초기화시킴
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Member f SET f.contents_count = 15 WHERE f.role = 'ROLE_USER' and f.plan = 'free'")
    int free_init();
    
    // 특정 아이디를 가진 회원의 플랜 정보 가져오기
    @Query("select f.plan from Member f where f.id = ?1")
     String findPlanById(String id);

    // 특정 아이디를 가진 회원의 플랜 만료일 가져오기
    @Query("select f.subscription_end_date from Member f where f.id = ?1")
    LocalDateTime findEndDateById(String id);

    // 플랜이 만료된 경우 plan 은 free, subscription_end_date 은 null 로 update
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Member f SET f.plan = 'free', f.subscription_end_date = null WHERE f.id = :userId and f.role = 'ROLE_USER' and f.plan = 'premium'")
    int handleExpiredPlan(@Param("userId") String userId);

    
    // 만료일이 start~end 사이에 있는 유저 리스트 리턴
    @Query("SELECT f FROM Member f WHERE f.role = 'ROLE_USER' AND f.plan = 'premium' AND f.subscription_end_date BETWEEN :start AND :end")
    List<Member> findMembersExpiringBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
