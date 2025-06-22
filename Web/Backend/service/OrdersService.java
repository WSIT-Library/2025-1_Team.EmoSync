package com.example.capstone.service;

import com.example.capstone.dto.OrdersDto;
import com.example.capstone.entity.Member;
import com.example.capstone.entity.Orders;
import com.example.capstone.repository.OrdersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OrdersService {
    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private MemberService memberService;

    @Transactional
    public Orders insert(OrdersDto ordersDto) {
        // 필드 값이 null 이거나 빈 문자열인 경우 체크
        if (hasNullOrEmptyField(ordersDto)) {
            return null;
        }
        // 결제 내역 검증
        if(!ordersValueCheck(ordersDto)){
            return null;
        }

        // dto -> entity 변환
        Orders NewOrders = Orders.builder()
                .mpaynum(ordersDto.getMpaynum())
                .memberid(ordersDto.getMemberid())
                .membername(ordersDto.getMembername())
                .mpaymethod(ordersDto.getMpaymethod())
                .mpayproduct(ordersDto.getMpayproduct())
                .mpayprice(ordersDto.getMpayprice())
                .mpaydate(ordersDto.getMpaydate())
                .build();

        return ordersRepository.save(NewOrders);
    }

    // 결제 내역 검증
    private boolean ordersValueCheck(OrdersDto ordersDto) {
        Member member = memberService.UserInfo();

        if (!member.getId().equals(ordersDto.getMemberid()) ||
                !member.getName().equals(ordersDto.getMembername()) ||
                !ordersDto.getMpayproduct().equals("EmoSync Premium 요금제") ||
                !ordersDto.getMpayprice().equals("5900")) {
            return false;
        }
        return true;

    }

    // null 이거나 빈 문자열 체크 메서드
    private boolean hasNullOrEmptyField(OrdersDto ordersDto) {
        return isNullOrEmpty(ordersDto.getMpaynum()) ||
                isNullOrEmpty(ordersDto.getMemberid()) ||
                isNullOrEmpty(ordersDto.getMembername()) ||
                isNullOrEmpty(ordersDto.getMpaymethod()) ||
                isNullOrEmpty(ordersDto.getMpayproduct()) ||
                isNullOrEmpty(ordersDto.getMpayprice()) ||
                ordersDto.getMpaydate() == null;
    }

    // 문자열이 null 이거나 빈 문자열인지 체크하는 메서드
    private boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }
}
