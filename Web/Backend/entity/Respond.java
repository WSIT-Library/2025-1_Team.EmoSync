package com.example.capstone.entity;

import com.example.capstone.dto.RespondDto;
import com.example.capstone.respondTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Objects;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
public class Respond extends respondTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 답변 id

    @JoinColumn(name="inquiry_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Inquiry inquiry; // 답변이 작성될 문의글 아이디

    @JoinColumn(name = "writer_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Member res_member; // 답변 작성자 아이디

    @Column
    private String body; // 답변 내용

    // 댓글 수정
    public void patch(RespondDto dto) {
        //예외 발생
        if(!Objects.equals(this.id, dto.getId())){
            throw new IllegalArgumentException("댓글 수정 실패! 잘못된 id가 입력됐습니다.");
        }

        //객체 갱신
        if(dto.getBody() != null) {
            this.body = dto.getBody();
        }
    }

    // 댓글 생성
    public static Respond createRespond(RespondDto dto, Inquiry inquiry, Member member) {
        //예외 발생
        if(dto.getId() != null) {
            throw new IllegalArgumentException("댓글 생성 실패! 댓글의 Id가 없어야 합니다.");
        }
        if(!Objects.equals(dto.getInquiryId(), inquiry.getId())) {
            throw new IllegalArgumentException("댓글 생성 실패! 게시글의 Id가 잘못됐습니다");
        }

        //엔티티 생성 및 반환
        return new Respond(
                null,
                inquiry,
                member,
                dto.getBody()
        );
    }
}
