package com.example.capstone.handler;

import java.io.IOException;

import com.example.capstone.service.MemberService;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.RedirectStrategy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
public class CustomLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final MemberService memberService;

    private final RequestCache requestCache            = new HttpSessionRequestCache();
    private final RedirectStrategy redirectStrategy    = new DefaultRedirectStrategy();

    public CustomLoginSuccessHandler(MemberService memberService) {
        this.memberService = memberService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response
            , FilterChain chain, Authentication authentication) throws IOException, ServletException {
        AuthenticationSuccessHandler.super.onAuthenticationSuccess(request, response, chain, authentication);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 로그인한 사용자 ID
        String LoginId = authentication.getName();

        SavedRequest savedRequest = requestCache.getRequest(request, response);

        // 로그인 성공시 플랜 만료 여부 체크
        memberService.CheckPlan(LoginId);

        // 접근 권한 없는 경로로 접근해서 스프링 시큐리티가 인터셉트 후 로그인 페이지로 이동 한 경우
        if (savedRequest != null) {
            String targetUrl = savedRequest.getRedirectUrl();
            redirectStrategy.sendRedirect(request, response, targetUrl);
        }
        // 로그인 버튼 눌러서 로그인 페이지로 이동 한 경우
        else {
            redirectStrategy.sendRedirect(request, response, "/pages/index");
        }

    }
}
