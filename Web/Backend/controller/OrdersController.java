package com.example.capstone.controller;

import com.example.capstone.dto.OrdersDto;
import com.example.capstone.entity.Member;
import com.example.capstone.entity.Orders;
import com.example.capstone.service.MemberService;
import com.example.capstone.service.OrdersService;
import com.example.capstone.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@Slf4j
public class OrdersController {
    @Autowired
    private MemberService memberService;

    @Autowired
    private OrdersService ordersService;

    // 공통으로 사용되는 model
    @ModelAttribute
    public void ShardedModel(Model model) {
        model.addAttribute("userId", memberService.ReturnSessionUser("id"));
    }
    
    // 요금제 소개 페이지
    @GetMapping("/pages/plan/intro")
    public String planIntro(Model model) {
        String currentUserId = memberService.ReturnSessionUser("id");

        // 현재 유저가 가입한 요금제 종류
        String currentPlan;

        // 만약 비로그인 상태인 경우
        if(currentUserId == null){
            currentPlan = "anonymousUser";
        }
        else{
            currentPlan = memberService.find_plan(currentUserId);
        }

        model.addAttribute("currentPlan", currentPlan);
        return "pages/plan/planIntro";
    }

    // 요금제 결제 페이지
    @GetMapping("/pages/plan/payment")
    public String planPayment(Model model) {
        // 유저 정보
        Member UserInfo = memberService.UserInfo();

        // 현재 시간
        LocalDateTime now = LocalDateTime.now();
        // 날짜와 시간 포맷 설정 (예: yyyy-MM-dd HH:mm)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        // 날짜와 시간 포맷팅
        String formattedDateTime = now.format(formatter);

        // 플랜 만료 날짜
        LocalDateTime subscription_end_date = memberService.cal_subscription_end_date(UserInfo);


        model.addAttribute("currentTime", formattedDateTime);
        model.addAttribute("UserInfo", UserInfo);
        model.addAttribute("subscription_end_date", subscription_end_date);
        return "pages/plan/planPayment";
    }

    // 결제 정보 저장 및 요금제 정보 업데이트
    @PostMapping("/pages/plan/payment/insertMPay")
    @ResponseBody
    public String insertMPay(@RequestBody OrdersDto ordersDto){
        // 결제 정보 DB 에 저장
        Orders orders = ordersService.insert(ordersDto);
        
        // 결제 정보 저장 실패
        if(orders == null){
            String msg = Util.url.encode("[결제 오류] 결제 정보 검증에 실패하였습니다.");
            return "/members/error/" + msg;
        }

        // 요금제 정보 업데이트
        Member updatePlan = memberService.updatePlan(ordersDto);

        if(updatePlan == null){
            String msg = Util.url.encode("[요금제 정보 업데이트 오류] 요금제 정보 업데이트에 실패하였습니다.");
            return "/members/error/" + msg;
        }
        
        return "/members/MyPage_UserPlan";
    }
}
