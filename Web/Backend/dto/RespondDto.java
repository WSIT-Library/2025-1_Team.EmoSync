package com.example.capstone.dto;

import com.example.capstone.entity.Respond;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.time.LocalDateTime;


@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class RespondDto {
    private Long id;
    private Long inquiryId;
    private String inquiryWriterId;
    private String writerId;
    private String body;
    private LocalDateTime createDate;
    private LocalDateTime modifiedDate;

    // entity -> dto 변환 (출력용)
    public static RespondDto creatRespondDto(Respond respond) {
        return new RespondDto(
                respond.getId(),
                respond.getInquiry().getId(),
                respond.getInquiry().getMember().getId(),
                respond.getRes_member().getId(),
                respond.getBody(),
                respond.getCreatedDate(),
                respond.getModifiedDate()
        );
    }
}
