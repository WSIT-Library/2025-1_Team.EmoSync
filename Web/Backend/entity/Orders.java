package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
public class Orders {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 테이블 기본키

    @Column
    private String mpaynum; // 결제번호

    @Column
    private String memberid; // 아이디

    @Column
    private String membername; // 이름

    @Column
    private String mpaymethod; //결제방식

    @Column
    private String mpayproduct; //상품명

    @Column
    private String mpayprice; // 가격

    @Column
    private LocalDateTime mpaydate; // 결제날짜
}
