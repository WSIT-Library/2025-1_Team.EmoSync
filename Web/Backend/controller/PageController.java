package com.example.capstone.controller;

import com.example.capstone.service.MemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@Slf4j
public class PageController {
    @Autowired
    private MemberService memberService;

    // 공통으로 사용되는 model
    @ModelAttribute
    public void ShardedModel(Model model) {
        model.addAttribute("userId", memberService.ReturnSessionUser("id"));
    }

    // 홈페이지
    @GetMapping("/pages/index")
    public String index(Model model) {
        return "pages/index";
    }
}
