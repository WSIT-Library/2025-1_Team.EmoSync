package com.example.capstone.dto;

import jakarta.validation.constraints.Null;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@AllArgsConstructor
@ToString
@Getter
@Builder(toBuilder = true)
public class InquiryDto {
    private Long id;
    private String title;
    private String content;
    private Integer views;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
    private String category;
    private String secret;

    @Null
    private String writerID;

    @Null
    private String image;
}
