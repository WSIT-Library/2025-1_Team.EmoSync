package com.example.capstone.entity;

import com.example.capstone.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;

import java.util.List;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
@Getter
@DynamicInsert
@Builder(toBuilder = true)
public class Inquiry extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; //글 ID

    @JoinColumn(name = "writerid")
    @ManyToOne(fetch = FetchType.LAZY)
    private Member member; //글 작성자 아이디

    @Column
    private String title; //글 제목

    @Column(columnDefinition = "TEXT")
    private String content; //글 내용

    @ColumnDefault("0")
    private Integer views; //글 조회수


    @Column
    private String respond; // 답변 여부(Y/N)

    @Column
    private String image; //글 이미지 파일 이름 목록

    @Column
    private String category; // 문의 유형(서비스, 회원, 기타)

    @Column
    private String secret; // 비밀글 여부(Y/N)

    @OneToMany(mappedBy = "inquiry", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Respond> Responds;
}
