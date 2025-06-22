package com.example.capstone.controller;

import com.example.capstone.dto.InquiryDto;
import com.example.capstone.dto.RespondDto;
import com.example.capstone.entity.Inquiry;
import com.example.capstone.service.InquiryService;
import com.example.capstone.service.MemberService;
import com.example.capstone.service.RespondService;
import com.example.capstone.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@Slf4j
public class InquiryController {

    @Autowired
    private MemberService memberService;

    @Autowired
    private InquiryService inquiryService;

    @Autowired
    private RespondService respondService;

    @Value("${app.FileRoot}")
    private String FILE_ROOT;

    // 공통으로 사용되는 model
    @ModelAttribute
    public void ShardedModel(Model model) {
        model.addAttribute("userId", memberService.ReturnSessionUser("id"));
        model.addAttribute("FileRoot", FILE_ROOT);
    }

    // 고객문의 게시판 페이지
    @GetMapping("/pages/inquiry/inquiryBoard")
    public String inquiryBoard(Model model, Pageable pageable) {
        Page<Inquiry> InquiryList = inquiryService.get_all_inquiry(pageable);
        model.addAttribute("InquiryLists", InquiryList);
        return "pages/inquiry/inquiryBoard";
    }

    // 문의글 작성 페이지
    @GetMapping("/pages/inquiry/inquiryNew")
    public String index(Model model) {
        return "pages/inquiry/inquiryNew";
    }

    // 게시글 작성, 삭제 성공시 알림 페이지
    @GetMapping("/pages/inquiry/Success/{msg}")
    public String inquiry_Success(Model model, @PathVariable String msg) {
        String message = msg.replace("+", " ");
        model.addAttribute("message", message);
        return "pages/inquiry/inquirySuccess";
    }

    // 게시글 수정 성공시 알림 페이지
    @GetMapping("/pages/inquiryEdit/Success/{id}")
    public String inquiry_Success(Model model, @PathVariable Long id) {
        model.addAttribute("inquiryId", id);
        return "pages/inquiry/inquiryEditSuccess";
    }

    // 게시글 작성 요청 처리
    @PostMapping("/inquiry/create")
    public String createForum(InquiryDto inquiryDto) {

        // service 로 게시글 작성 요청
        String result = inquiryService.create(inquiryDto);

        // 결과가 정상적으로 처리된 경우
        if(result.equals("Success")) {
            String msg = Util.url.encode("문의글이 작성되었습니다.");
            return "redirect:/pages/inquiry/Success/" + msg;
        }
        // 실패한 경우 에러 메세지와 함께 에러 페이지로 이동
        String msg = Util.url.encode(result);
        return "redirect:/members/error/" + msg;
    }

    // 게시글 조회
    @GetMapping("/pages/inquiry/{id}")
    public String show(@PathVariable Long id, Model model) {
        if(id == null){
            String msg = Util.url.encode("존재하지 않는 글 입니다.");
            return "redirect:/members/error/" + msg;
        }

        // 현재 로그인한 사용자 ID
        String LoginUserId = memberService.ReturnSessionUser("id");
        
        // 로그인한 사용자의 권한 가져오기
        String LoginUserRole = memberService.find_role(LoginUserId);

        // 게시글 정보 가져오기
        Inquiry inquiryEntity = inquiryService.show(id);

        // 문의글이 비밀글인 경우
        if(inquiryEntity.getSecret().equals("Y")) {
            // 해당 글 작성자 본인과 관리자만 열람할 수 있도록 제한
            if(LoginUserId.equals(inquiryEntity.getMember().getId()) || LoginUserRole.equals("ROLE_ADMIN")){
                //조회수 증가
                String view_update_result = inquiryService.view_update(id);
                
                // 조회수 증가가 제대로 진행 됐는지 검사
                if(!view_update_result.equals("Success")) {
                    String msg = Util.url.encode(view_update_result);
                    return "redirect:/members/error/" + msg;
                }

                // 게시글에 작성된 댓글들 조회
                List<RespondDto> respondDto = respondService.show_respond(id);

                model.addAttribute("inquiry", inquiryEntity);
                model.addAttribute("respondDto", respondDto);
                return "pages/inquiry/inquiryShow";
            }
            else{
                String msg = Util.url.encode("접근 권한이 없습니다.");
                return "redirect:/members/error/" + msg;
            }
        }
        // 문의글이 전체 공개인 경우
        else{
            //조회수 증가
            String view_update_result = inquiryService.view_update(id);

            // 조회수 증가가 제대로 진행 됐는지 검사
            if(!view_update_result.equals("Success")) {
                String msg = Util.url.encode(view_update_result);
                return "redirect:/members/error/" + msg;
            }

            // 게시글에 작성된 댓글들 조회
            List<RespondDto> respondDto = respondService.show_respond(id);

            model.addAttribute("inquiry", inquiryEntity);
            model.addAttribute("respondDto", respondDto);
            return "pages/inquiry/inquiryShow";
        }
    }

    // 게시글 수정 페이지
    @GetMapping("/pages/inquiry/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        
        // 현재 로그인한 사용자 ID
        String LoginUserId = memberService.ReturnSessionUser("id");

        // 로그인한 사용자의 권한 가져오기
        String LoginUserRole = memberService.find_role(LoginUserId);
        
        // 로그인한 사용자가 관리자가 아닌 일반 유저인 경우
        if(!LoginUserRole.equals("ROLE_ADMIN")) {
            // 수정하려는 글의 작성자와 로그인한 사용자 아이디를 비교
            boolean result = inquiryService.SearchID(id, LoginUserId);

            // 동일한 사람이 아닌 경우
            if(!result){
                String msg = Util.url.encode("접근 권한이 없거나 잘못된 접근 입니다.");
                return "redirect:/members/error/" + msg;
            }

            // 문의글 답변 여부 가져오기
            String respond_ck = inquiryService.getRespond(id);
            if(respond_ck.equals("Y")) {
                String msg = Util.url.encode("답변 완료된 문의글은 수정이 불가능 합니다.");
                return "redirect:/members/error/" + msg;
            }
        }

        // service 로 수정 요청
        InquiryDto inquiryDto = inquiryService.show_update(id);

        // 게시글이 없거나 게시글 작성자가 탈퇴한 경우
        if(inquiryDto == null){
            String msg = Util.url.encode("문의글이 없습니다.");
            return "redirect:/members/error/" + msg;
        }

        model.addAttribute("inquiry", inquiryDto);
        return "pages/inquiry/inquiryEdit";
    }

    // 게시글 수정 요청 처리
    @PostMapping("/inquiry/update")
    public String update(InquiryDto inquiryDto) {
        log.info("inquiryDto: {}", inquiryDto);
        Inquiry target = inquiryService.update(inquiryDto);
        if(target == null){
            String msg = Util.url.encode("게시글 수정 오류");
            return "redirect:/members/error/" + msg;
        }
        return "redirect:/pages/inquiryEdit/Success/" + target.getId();
    }

    // 게시글 삭제 요청 처리
    @GetMapping("/pages/inquiry/{id}/delete")
    public String delete(@PathVariable Long id) {

        // 현재 로그인한 사용자 ID
        String LoginUserId = memberService.ReturnSessionUser("id");

        // 로그인한 사용자의 권한 가져오기
        String LoginUserRole = memberService.find_role(LoginUserId);

        // 로그인한 사용자가 관리자가 아닌 일반 유저인 경우
        if(!LoginUserRole.equals("ROLE_ADMIN")) {
            // 삭제하려는 글의 작성자와 로그인한 사용자 아이디를 비교
            boolean result = inquiryService.SearchID(id, LoginUserId);

            // 동일한 사람이 아닌 경우
            if(!result){
                String msg = Util.url.encode("접근 권한이 없거나 잘못된 접근 입니다.");
                return "redirect:/members/error/" + msg;
            }

            // 문의글 답변 여부 가져오기
            String respond_ck = inquiryService.getRespond(id);
            if(respond_ck.equals("Y")) {
                String msg = Util.url.encode("답변 완료된 문의글은 삭제가 불가능 합니다.");
                return "redirect:/members/error/" + msg;
            }
        }

        // service 로 삭제 요청
        String delete_result = inquiryService.delete(id);

        // 결과가 정상적으로 처리된 경우
        if(delete_result.equals("Success")) {
            String msg = Util.url.encode("문의글이 삭제되었습니다.");
            return "redirect:/pages/inquiry/Success/" + msg;
        }
        else {
            String msg = Util.url.encode(delete_result);
            return "redirect:/members/error/" + msg;
        }
    }
}
