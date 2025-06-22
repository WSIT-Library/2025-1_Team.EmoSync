package com.example.capstone.controller;

import com.example.capstone.dto.MemberDto;
import com.example.capstone.dto.ResponseDto;
import com.example.capstone.entity.Member;
import com.example.capstone.service.MemberService;
import com.example.capstone.util.Util;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

import java.util.ArrayList;

@Controller
@Slf4j
public class MemberController {
    @Autowired
    private MemberService memberService;

    // 공통으로 사용되는 model
    @ModelAttribute
    public void ShardedModel(Model model) {
        model.addAttribute("userId", memberService.ReturnSessionUser("id"));
    }

    // 에러 페이지
    @GetMapping("/members/error/{msg}")
    public String error(Model model, @PathVariable String msg) {
        String error_message = msg.replace("+", " ");
        model.addAttribute("error_message", error_message);
        return "members/error";
    }
    
    // 로그인 페이지
    @GetMapping("/members/login")
    public String login() {return "members/login";}

    // 로그인 과정에서 오류 발생시 이동하는 페이지
    @GetMapping("/members/error_login")
    public String loginPage(@RequestParam(value = "error", required = false) String error, Model model) {

        // 에러를 모델에 담아 view resolve
        model.addAttribute("error", error);
        return "members/login";
    }

    // 로그아웃 버튼을 누른 경우
    @GetMapping("/members/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 현재 로그인 정보가 있는 경우
        if(authentication != null){
            new SecurityContextLogoutHandler().logout(request,response,authentication);
            return "redirect:/pages/index";
        }
        // 현재 로그인 정보가 없는 경우
        else {
            return "redirect:/members/error/잘못된 접근";
        }

    }

    // 회원가입 페이지
    @GetMapping("/members/register")
    public String register() {
        return "members/register";
    }


    // id 중복 체크
    @GetMapping("/members/IDCheck/{id}")
    public @ResponseBody ResponseDto<?> IDCheck(@PathVariable String id){

        if (!memberService.IDCheck(id)){
            return new ResponseDto<>(1,"동일한 아이디가 존재합니다.", false);
        }else{
            return new ResponseDto<>(1,"사용가능한 아이디입니다.", true);
        }
    }

    // 이메일 중복 체크
    @GetMapping("/members/EmailCheck/{email}")
    public @ResponseBody ResponseDto<?> EmailCheck(@PathVariable String email){

        if (!memberService.EmailCheck(email)){
            return new ResponseDto<>(1,"동일한 이메일이 존재합니다.", false);
        }else{
            return new ResponseDto<>(1,"사용가능한 이메일 입니다.", true);
        }
    }

    //회원가입 양식을 작성하고 가입을 누른 경우
    @PostMapping("/members/join")
    public String createMember(MemberDto memberDto, @RequestParam("inputCode") String inputCode, HttpSession session) {

        // form 에서 받은 데이터 값 유효성 검증
        if(memberDto.getId() == null || memberDto.getPassword() == null
                || memberDto.getEmail() == null || inputCode == null || memberDto.getName() == null){
            String msg = Util.url.encode("[회원가입 오류] 입력한 값이 유효하지 않습니다.");
            return "redirect:/members/error/" + msg;
        }

        Member saved = memberService.create(memberDto, inputCode, session);

        // 이미 db에 동일한 회원 정보가 있는 경우, 인증번호가 일치하지 않는 경우
        if(saved == null){
            String msg = Util.url.encode("[회원가입 오류] 인증번호가 유효하지 않거나 중복된 값이 존재합니다.");
            return "redirect:/members/error/" + msg;
        }
        else{
            // 회원가입이 정상적으로 진행되면 세션에 저장되어 있는 이메일 인증번호를 삭제한다
            session.removeAttribute("verificationCode");
            return "redirect:/members/register_success";
        }
    }

    // 회원가입 완료시 이동하는 페이지
    @GetMapping("/members/register_success")
    public String signUpSuccessPage() {
        return "members/register_success";
    }


    //  회원/요금제 정보 메뉴를 클릭한 경우
    @GetMapping("/members/MyPage_UserInfo")
    public String MyPage_UserInfo(Model model) {
        Member target = memberService.UserInfo();
        model.addAttribute("UserInfo", target);
        return "members/myPage/userInfo";
    }

    // 요금제 정보 메뉴 클릭한 경우
    @GetMapping("/members/MyPage_UserPlan")
    public String MyPage_UserPlan(Model model) {
        Member target = memberService.UserInfo();
        model.addAttribute("UserInfo", target);
        return "members/myPage/userPlan";
    }

    // 비밀번호 변경 페이지
    @GetMapping("/members/updatePassword")
    public String Password_ChangePage(Model model) {return "members/myPage/update/updatePassword";}

    // (회원탈퇴, 비밀번호 수정)입력한 비밀번호와 db 에 저장된 비밀번호를 비교
    @PostMapping("/infoCheck/verifyPassword")
    public ResponseEntity<Boolean> verifyCode(@RequestParam("inputId") String inputId, @RequestParam("inputPassword") String inputPassword) {
        // 아이디와 비밀번호를 받아서 service 로 전송
        boolean result = memberService.verifyPassword(inputId, inputPassword);

        if (result) {
            return ResponseEntity.ok(true);
        } else {
            return ResponseEntity.ok(false);
        }
    }

    // 비밀번호 변경 요청 처리
    @PostMapping("/members/req_update_password")
    public String updatePassword(String id, String currentPassword, String newPassword, String inputCode, HttpSession session) {
        if(id == null || currentPassword == null || newPassword == null || inputCode == null){
            String msg = Util.url.encode("[비밀번호 변경 오류] 입력한 값이 유효하지 않습니다.\n(입력 값이 제대로 전송되지 않음)");
            return "redirect:/members/error/" + msg;
        }
        boolean result = memberService.updatePassword(id, currentPassword, newPassword, inputCode, session);
        // 비밀번호 변경이 성공한 경우
        if(result){
            String msg = Util.url.encode("비밀번호");
            return "redirect:/members/updateSuccess/" + msg;
        }
        // 비밀번호 변경이 실패한 경우 에러 페이지로 이동
        else{
            String msg = Util.url.encode("[비밀번호 변경 오류] 입력한 값이 유효하지 않습니다.");
            return "redirect:/members/error/" + msg;
        }
    }

    // 이메일 변경 전 이메일 인증 페이지
    @GetMapping("/members/updateEmail_Auth")
    public String EmailAuth_ChangePage(Model model) {return "members/myPage/update/updateEmail_Auth";}

    // 이메일 변경 전 이메일 인증에 대한 요청 처리
    @PostMapping("/members/req_update_email_auth")
    public String updateEmail_Auth(String inputCode, HttpSession session) {
        boolean result = memberService.updateEmail_Auth(inputCode, session);
        // 이메일 인증이 성공한 경우
        if(result){
            // 이메일 변경 페이지로 이동
            return "redirect:/members/updateEmail";
        }
        // 이메일 인증이 실패한 경우 에러 페이지로 이동
        else{
            String msg = Util.url.encode("[이메일 인증 오류] 인증번호가 유효하지 않습니다.");
            return "redirect:/members/error/" + msg;
        }
    }

    // 이메일 변경 페이지
    @GetMapping("/members/updateEmail")
    public String Email_ChangePage(Model model, HttpSession session) {
        // 세션에 저장된 변수를 불러옮
        String session_Auth = (String) session.getAttribute("updateEmail_Auth");
        // 이메일 인증이 통과된 경우 세션 변수에 ok 라는 값이 저장되어 있음
        if(session_Auth == null){
            String msg = Util.url.encode("[접근 오류] 이메일 인증이 필요합니다.");
            return "redirect:/members/error/" + msg;
        }
        if(session_Auth.equals("ok")){
            // 세션 검사를 끝냈으니 세션 변수 삭제
            session.removeAttribute("updateEmail_Auth");
            return "members/myPage/update/updateEmail";
        }
        else{
            String msg = Util.url.encode("[접근 오류] 이메일 인증이 필요합니다.");
            return "redirect:/members/error/" + msg;
        }
    }

    // 이메일 변경 요청 처리
    @PostMapping("/members/req_update_email")
    public String updateEmail(String id, String newEmail) {
        if (id == null || newEmail == null){
            String msg = Util.url.encode("[이메일 변경 오류] 입력한 값이 유효하지 않습니다.");
            return "redirect:/members/error/" + msg;
        }
        boolean result = memberService.updateEmail(id, newEmail);
        // 이메일 변경이 성공한 경우
        if(result){
            String msg = Util.url.encode("이메일");
            return "redirect:/members/updateSuccess/" + msg;
        }
        // 이메일 변경이 실패한 경우 에러 페이지로 이동
        else{
            String msg = Util.url.encode("[이메일 변경 오류] 이메일 중복 또는 회원정보가 존재하지 않습니다.");
            return "redirect:/members/error/" + msg;
        }
    }

    // update 성공 페이지
    @GetMapping("/members/updateSuccess/{msg}")
    public String updateSuccess(Model model, @PathVariable String msg) {
        String message = msg.replace("+", " ");
        model.addAttribute("message", message);
        return "members/myPage/update/updateSuccess";
    }

    // 회원탈퇴 페이지
    @GetMapping("/members/deleteMember")
    public String Member_DeletePage(Model model) {return "members/myPage/delete/deleteMember";}

    // 회원탈퇴 요청 처리
    @PostMapping("/members/req_delete_member")
    public String deleteMember(String id, String password, String inputCode, HttpSession session) {
        // 아이디, 비밀번호, 인증번호 중 하나라도 값이 들어있지 않은 경우
        if(id == null || password == null || inputCode == null){
            String msg = Util.url.encode("[회원탈퇴 오류] 입력하신 값이 유효하지 않습니다.");
            return "redirect:/members/error/" + msg;
        }

        // service 로 회원탈퇴 요청
        boolean result = memberService.deleteMember(id, password, inputCode, session);

        // 회원 탈퇴가 성공한 경우
        if(result){
            String msg = Util.url.encode("회원탈퇴");
            return "redirect:/members/deleteSuccess/" + msg;
        }
        // 회원 탈퇴가 실패한 경우 에러 페이지로 이동
        else{
            String msg = Util.url.encode("[회원탈퇴 오류] 입력하신 값이 유효하지 않습니다.");
            return "redirect:/members/error/" + msg;
        }
    }

    // delete 성공 페이지
    @GetMapping("/members/deleteSuccess/{msg}")
    public String deleteSuccess(Model model, @PathVariable String msg) {
        String message = msg.replace("+", " ");
        model.addAttribute("message", message);
        return "members/myPage/delete/deleteSuccess";
    }

    // 아이디 찾기 페이지
    @GetMapping("/members/find_id")
    public String Find_IdPage() {
        return "members/find/find_id";
    }

    // 아이디 찾기(이메일 존재 여부 검사)
    @PostMapping("/infoCheck/EmailExist")
    public ResponseEntity<Boolean> EmailExist(@RequestParam("email") String email) {
        // 이메일을 받아서 service 로 전송
        boolean result = memberService.EmailExist(email);

        if (result) {
            return ResponseEntity.ok(true);
        } else {
            return ResponseEntity.ok(false);
        }
    }

    // 비밀번호를 찾고자 하는 아이디 입력 페이지(비밀번호 찾기)
    @GetMapping("/members/find_pw_IdAuth")
    public String find_pw_IdAuthPage(HttpSession session) {
        // 혹시 이전에 입력한 세션 변수가 남아있으면 안되기 때문에 삭제
        session.removeAttribute("IdAuth");
        return "members/find/find_pw_IdAuth";
    }

    // 비밀번호를 찾고자 하는 아이디 입력 요청을 처리(비밀번호 찾기)
    @PostMapping("/members/req_find_pw_IdAuth")
    public String req_find_pw_IdAuth(@RequestParam("id") String id, HttpSession session) {
        // 아이디 값이 들어있지 않은 경우
        if(id == null){
            String msg = Util.url.encode("[비밀번호 찾기 오류] 아이디가 입력되지 않았습니다.");
            return "redirect:/members/error/" + msg;
        }

        // service 로 아이디값 전송
        boolean result = memberService.find_pw_IdAuth(id, session);

        // 아이디가 존재하는 경우 이메일 인증 단계로 넘어감
        if(result){
            return "redirect:/members/find_pw_EmailAuth";
        }
        // 아이디가 존재하지 않는 경우 에러 페이지로 이동
        else{
            String msg = Util.url.encode("[비밀번호 찾기 오류] 존재하지 않는 아이디 입니다.");
            return "redirect:/members/error/" + msg;
        }
    }

    // 비밀번호 찾기- 이메일 인증 페이지
    @GetMapping("/members/find_pw_EmailAuth")
    public String find_pw_EmailAuthPage(HttpSession session, Model model) {
        // 세션에 저장된 변수를 불러옴(이전 페이지에서 입력한 아이디 값)
        String session_Auth = (String) session.getAttribute("IdAuth");
        // 현재 세션에 저장된 아이디 값이 없는 경우(이전 단계를 거치지 않고 바로 접속한 경우)
        if(session_Auth == null){
            String msg = Util.url.encode("[비밀번호 찾기 오류] 잘못된 접근 입니다.");
            return "redirect:/members/error/" + msg;
        }
        model.addAttribute("session_Auth", session_Auth);
        return "members/find/find_pw_EmailAuth";
    }

    // 임시 비밀번호 발송 완료 메세지
    @GetMapping("/members/find_pw_success")
    public String find_pw_EmailSend_Success() {
        return "members/find/find_pw_success";
    }

}
