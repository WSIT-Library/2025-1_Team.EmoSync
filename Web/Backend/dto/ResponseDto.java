package com.example.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter

// id, nickname 중복 검사 결과를 ajax에 넘겨주기 위해서 만든 객체
public class ResponseDto<T> {
    private int code;
    private String msg;
    private T data;
}
