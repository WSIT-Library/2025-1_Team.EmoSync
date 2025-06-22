package com.example.capstone.dto;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class OrdersDto {
    String mpaynum;
    String memberid;
    String membername;
    String mpaymethod;
    String mpayproduct;
    String mpayprice;
    LocalDateTime mpaydate;
}
