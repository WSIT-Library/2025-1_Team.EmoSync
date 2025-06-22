package com.example.capstone.handler;

import com.example.capstone.util.Util;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

public class AuthenticationAccessDeniedHandler implements AccessDeniedHandler {
    private String denied_url;

    public AuthenticationAccessDeniedHandler() {
        String msg = Util.url.encode("[오류] 잘못된 접근입니다.");
        this.denied_url = "/members/error/" + msg;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.sendRedirect(request.getContextPath() + denied_url);
    }
}
