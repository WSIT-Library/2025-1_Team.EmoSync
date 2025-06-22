package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
public class Member {
    @Id
    private String id; // 회원 ID

    @Column
    private String password; // 회원 password

    @Column(unique = true)
    private String email; // 회원 email

    @Column
    private String name;

    @Column
    private String role; // 회원 권한(user or admin)

    @Column
    private Integer contents_count; // 일일 콘텐츠 생성 가능 횟수

    @Column
    private String plan; // 요금제(free, premium)

    @Column
    private LocalDateTime subscription_end_date; // 유료 플랜의 종료 날짜

    @OneToMany(mappedBy = "member", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Inquiry> writerInquiry; // 문의글 작성자 정보

    @OneToMany(mappedBy = "res_member", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Respond> responds; // 답변

    @OneToMany(mappedBy = "con_member", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contents> Contents; // 콘텐츠

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member member)) return false;
        return Objects.equals(id, member.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
