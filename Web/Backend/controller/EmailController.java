package com.example.capstone.controller;

import com.example.capstone.service.EmailService;
import com.example.capstone.service.MemberService;
import com.example.capstone.util.Util;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@Slf4j
public class EmailController {

    private final EmailService emailService;

    private final MemberService memberService;

    // 비로그인 상태 이메일 전송 (회원가입, 새로운 이메일로 변경 후 인증)
    @PostMapping("/infoCheck/sendEmail")
    public ResponseEntity<Void> sendEmail(@RequestParam("email") String email, HttpSession session) throws MessagingException {
        // 이메일 전송 및 인증번호 저장
        String verificationCode = emailService.sendVerificationCode(email);
        session.setAttribute("verificationCode", verificationCode); // 세션에 전송된 인증번호를 저장.
        return ResponseEntity.ok().build();
    }

    // 입력한 인증번호와 세션에 저장된 인증번호를 비교
    @PostMapping("/infoCheck/verifyCode")
    public ResponseEntity<Boolean> verifyCode(@RequestParam("inputCode") String inputCode, HttpSession session) {

        String storedCode = (String) session.getAttribute("verificationCode");

        if (storedCode != null && storedCode.equals(inputCode)) {
            return ResponseEntity.ok(true);
        } else {
            return ResponseEntity.ok(false);
        }
    }

    // 이메일 전송 (회원탈퇴, 비밀번호 변경, 이메일 변경, 비밀번호 찾기를 위한 이메일 인증)
    @PostMapping("/infoCheck/sendEmail_user")
    public ResponseEntity<Boolean> sendEmail_user(@RequestParam("id") String id, HttpSession session) throws MessagingException {
        // 받은 id로 이메일 검색 후 저장
        String email = memberService.searchEmail(id);
        
        // 이메일 주소가 존재하지 않는 경우
        if(email == null) {
            return ResponseEntity.ok(false);
        }

        // 이메일 전송 및 인증번호 저장
        String verificationCode = emailService.sendVerificationCode(email);
        session.setAttribute("verificationCode", verificationCode); // 세션에 전송된 인증번호를 저장.
        return ResponseEntity.ok(true);
    }

    // 비로그인 상태 이메일 전송 (아이디 찾기)
    @PostMapping("/infoCheck/sendEmail_find")
    public ResponseEntity<Void> sendEmail_find(@RequestParam("email") String email) throws MessagingException {
        // 이메일로 ID 전송
        emailService.sendUserId(email);
        return ResponseEntity.ok().build();
    }

    // 비로그인 상태 이메일로 임시 비밀번호 전송 (비밀번호 찾기)
    @PostMapping("/infoCheck/sendEmail_pw")
    public String sendEmail_pw(@RequestParam("id") String id, @RequestParam("inputCode") String inputCode, HttpSession session) throws MessagingException {
        // 세션에 저장된 아이디 변수를 불러옴(이전에 입력한 아이디 값)
        String session_Auth = (String) session.getAttribute("IdAuth");
        // 세션에 저장된 값과 파라미터로 받아온 id 값의 동일성 검증
        if(!id.equals(session_Auth)){
            String msg = Util.url.encode("[비밀번호 찾기 오류] 아이디 값 변경이 감지되었습니다. 다시 시도하십시오.");
            return "redirect:/members/error/" + msg;
        }
        
        // 세션에 저장된 인증번호 가져옮
        String storedCode = (String) session.getAttribute("verificationCode");
        //  세션에 저장된 인증번호와 입력한 인증번호가 일치하지 않으면 에러
        if (storedCode == null || !storedCode.equals(inputCode)) {
            return "redirect:/members/access_error";
        }

        // 세션 검사가 끝나면 세션 변수 값 삭제
        session.removeAttribute("verificationCode");

        // 입력받은 아이디로 이메일 주소 찾음
        String email = memberService.findEmail(id);
        // 이메일로 임시 비밀번호 전송
        emailService.sendTempPassword(email, id);

        // 임시 비밀번호 전송하면 세션 변수 값 삭제
        session.removeAttribute("IdAuth");
        return "redirect:/members/find_pw_success";
    }
}
