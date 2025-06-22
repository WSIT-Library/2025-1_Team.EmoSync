package com.example.capstone.service;

import com.example.capstone.dto.CustomUserDetails;
import com.example.capstone.entity.Member;
import com.example.capstone.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String id) throws UsernameNotFoundException {

        // 코드에는 UsernameNotFoundException 라고 적혀있지만 실제로는 BadCredentialsException 를 리턴함
        Member userData = memberRepository.findById(id).orElseThrow(() ->
                new UsernameNotFoundException("사용자가 존재하지 않습니다."));
        return new CustomUserDetails(userData);
    }

}
