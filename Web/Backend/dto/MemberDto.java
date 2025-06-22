package com.example.capstone.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@Builder
public class MemberDto {
    private String id;
    private String password;
    private String email;
    private String name;
}
