package com.example.capstone.config;

import com.example.capstone.handler.AuthenticationAccessDeniedHandler;
import com.example.capstone.handler.CustomLoginSuccessHandler;
import com.example.capstone.handler.LoginFailureHandler;
import com.example.capstone.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final MemberService memberService;

    // 로그인 성공 핸들러
    @Bean
    public AuthenticationSuccessHandler customLoginSuccessHandler() {return new CustomLoginSuccessHandler(memberService);}

    // 로그인 상태에서 로그인/회원가입 페이지로 이동했을 경우 에러 처리 핸들러
    public AuthenticationAccessDeniedHandler authenticationAccessDeniedHandler(){
        return  new AuthenticationAccessDeniedHandler();
    }

    // 로그인 실패 핸들러
    public LoginFailureHandler loginFailureHandler(){
        return  new LoginFailureHandler();
    }


    // 동일한 브라우저에서 login-> logout -> login 시 세션이 최대라는 경고가 뜸
    // SessionRegistry, ServletListenerRegistrationBean 를 사용하여 해결
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }// Register HttpSessionEventPublisher

    @Bean
    public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    // 비밀번호 암호화
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {

        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{

        // 접근 제한
        http
                .authorizeHttpRequests((auth) -> auth
                        // 모두에게 접근 허용되는 주소
                        .requestMatchers("/pages/index", "/members/error/**", "/members/EmailCheck/**",
                                "/css/**", "/js/**", "/assets/**", "/infoCheck/verifyCode", "/infoCheck/sendEmail",
                                "/infoCheck/sendEmail_user", "/pages/plan/intro").permitAll()

                        // 비로그인 상태에서만 접근이 가능한 주소(로그인, 회원가입과 관련된 경로들)
                        .requestMatchers( "/members/login", "/members/register", "/members/IDCheck/**", "/members/join",
                                "/members/error_login", "/members/register_success", "/members/LoginMember", "/members/find_id",
                                "/infoCheck/EmailExist", "/infoCheck/sendEmail_find", "/members/find_pw_IdAuth", "/members/find_pw_EmailAuth",
                                "/members/req_find_pw_IdAuth", "/infoCheck/sendEmail_pw", "/members/find_pw_success").anonymous()

                        // ADMIN 권한이 있는 회원만 접근 가능한 주소
                        .requestMatchers("/admin").hasRole("ADMIN")
                        // ADMIN, USER 권한이 있는 회원만 접근 가능한 주소
                        .requestMatchers("/members/MyPage").hasAnyRole("ADMIN", "USER")
                        .anyRequest().authenticated()
                );


        http
                // error 403를 처리할 핸들러
                .exceptionHandling((exceptionConfig) ->
                        exceptionConfig.accessDeniedHandler(authenticationAccessDeniedHandler()));

        // 로그인
        http
                .formLogin((auth) -> auth
                        .loginPage("/members/login")
                        .loginProcessingUrl("/members/LoginMember")
                        .failureHandler(loginFailureHandler())
                        .successHandler(customLoginSuccessHandler())
                        .usernameParameter("id")
                );

        http
                .csrf(AbstractHttpConfigurer::disable);

        // 다중 로그인 제한
        http
                .sessionManagement((auth) -> auth
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(true));

        // 세션 고정 보호
        http
                .sessionManagement((auth) -> auth
                        .sessionFixation().changeSessionId());

        // 로그아웃
        http
                .logout((auth)->auth.logoutUrl("/members/logout")
                        .logoutSuccessUrl("/pages/index"));

        return http.build();
    }
}
