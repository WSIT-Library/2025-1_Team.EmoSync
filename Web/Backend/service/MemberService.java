package com.example.capstone.service;

import com.example.capstone.dto.MemberDto;
import com.example.capstone.dto.OrdersDto;
import com.example.capstone.entity.Member;
import com.example.capstone.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class MemberService {
    @Autowired
    private MemberRepository memberRepository;

    @Lazy
    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    // 세션 ID를 이용하여 현재 로그인한 사용자 ID 가져오기
    public String ReturnSessionUser(String request){
        String id = SecurityContextHolder.getContext().getAuthentication().getName();
        // 비로그인 상태인 경우 null 반환
        if(Objects.equals(id, "anonymousUser")){
            return null;
        }
        // 로그인 상태인 경우 id 반환
        else if(Objects.equals(request, "id")){
            return id;
        }
        else{
            return null;
        }
    }

    // 회원가입 시 ID 중복 체크(1차 검증)
    public Boolean IDCheck(String id) {
        boolean isId = memberRepository.existsById(id);
        if (isId) {
            // 중복인 경우
            return false;
        }
        else{
            // 중복이 아닌 경우
            return true;
        }
    }

    // 회원가입 시 이메일 중복 체크(1차 검증)
    public Boolean EmailCheck(String email) {
        boolean isEmail = memberRepository.existsByEmail(email);
        if (isEmail) {
            // 중복인 경우
            return false;
        }
        else{
            // 중복이 아닌 경우
            return true;
        }
    }

    // 회원가입 처리
    @Transactional
    public Member create(MemberDto dto, String inputCode, HttpSession session) {
        // 인증번호 유효성 검사
        String storedCode = (String) session.getAttribute("verificationCode");
        if (storedCode == null || !storedCode.equals(inputCode)) {
            return null;
        }

        //db에 id, email 이 하나라도 중복되는 값이 존재하는 경우를 검사(최종 검증)
        boolean isUser = memberRepository.
                existsByIdOrEmail(dto.getId(), dto.getEmail());
        if (isUser) {
            return null;
        }
        else {

            // dto -> entity 변환
            Member NewMember = Member.builder()
                    .id(dto.getId())
                    .password(bCryptPasswordEncoder.encode(dto.getPassword()))
                    .email(dto.getEmail())
                    .name(dto.getName())
                    .role("ROLE_USER")
                    .contents_count(15)
                    .plan("free")
                    .subscription_end_date(null)
                    .build();

            return memberRepository.save(NewMember);
        }
    }

    // 마이페이지에서 사용할 사용자 정보 가져오기
    public Member UserInfo() {
        return memberRepository.findById(ReturnSessionUser("id")).orElse(null);
    }


    // (회원탈퇴, 비밀번호 수정) 입력한 비밀번호와 db 에 저장된 비밀번호 비교
    public boolean verifyPassword(String inputId, String inputPassword) {
        // DB에 저장된 pw
        String savedPassword = memberRepository.findPasswordById(inputId);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();


        // 비밀번호가 일치한다면
        if(encoder.matches(inputPassword, savedPassword)){
            return true;
        }
        else{
            return false;
        }
    }

    // id 를 이용하여 이메일을 검색
    public String searchEmail(String id) {
        return memberRepository.findEmailById(id);
    }

    // 비밀번호 변경
    @Transactional
    public Boolean updatePassword(String id, String currentPassword, String newPassword, String inputCode, HttpSession session) {
        // 비밀번호 유효성 검증
        if(!verifyPassword(id, currentPassword)) {
            return false;
        }
        // 인증번호 유효성 검증
        String storedCode = (String) session.getAttribute("verificationCode");
        if (storedCode == null || !storedCode.equals(inputCode)) {
            return false;
        }

        // DB에 저장된 pw
        String savedPassword = memberRepository.findPasswordById(id);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();


        // 비밀번호가 일치한다면
        if(encoder.matches(currentPassword, savedPassword)){
            // 비밀번호 update 로직
            String securePassword = encoder.encode(newPassword);

            // 비밀번호 정보를 변경할 회원 정보 가져오기
            Member member = memberRepository.findById(id).orElse(null);

            // 회원 정보를 정상적으로 찾은 경우
            if(member != null) {
                Member memberPwModify = member.toBuilder()
                        .password(securePassword)
                        .build();

                memberRepository.save(memberPwModify);
                return true;
            }
            // 회원 정보를 찾지 못한 경우
            else{
                return false;
            }
        }
        // 비밀번호가 일치하지 않는다면
        else{
            return false;
        }
    }

    // 이메일 변경 전 이메일 인증에 대한 요청 처리
    public boolean updateEmail_Auth(String inputCode, HttpSession session) {
        // 인증번호 유효성 검증
        String storedCode = (String) session.getAttribute("verificationCode");
        if (storedCode == null || !storedCode.equals(inputCode)) {
            return false;
        }
        else {
            // 이메일 변경 페이지로의 이동을 제한하기 위한 세션 변수 설정
            session.setAttribute("updateEmail_Auth", "ok");
            return true;
        }
    }

    // 비상 연락용 이메일 변경
    @Transactional
    public boolean updateEmail(String id, String newEmail) {
        //db에 동일한 email 하나라도 중복되는 값이 존재하는 경우를 검사
        boolean isEmail = memberRepository.existsByEmail(newEmail);
        if (isEmail) {
            // 이메일 중복이 발생하여 null 반환
            return false;
        }
        else {
            // 이메일 정보를 변경할 회원 정보 가져오기
            Member member = memberRepository.findById(id).orElse(null);

            // 회원 정보를 정상적으로 찾은 경우
            if(member != null) {
                Member memberEmailModify = member.toBuilder()
                        .email(newEmail)
                        .build();
                memberRepository.save(memberEmailModify);
                return true;
            }
            // 회원 정보를 찾지 못한 경우
            else{
                return false;
            }
        }
    }

    // 회원탈퇴
    @Transactional
    public boolean deleteMember(String id, String password, String inputCode, HttpSession session) {
        // 비밀번호 유효성 검증
        if(!verifyPassword(id, password)) {
            return false;
        }
        // 인증번호 유효성 검증
        String storedCode = (String) session.getAttribute("verificationCode");
        if (storedCode == null || !storedCode.equals(inputCode)) {
            return false;
        }

        // 위의 유효성 검증을 통과하면 db 에서 회원 정보 delete
        memberRepository.deleteById(id);
        return true;
    }

    // 아이디 찾기(회원 정보에 등록된 이메일이 맞는지 확인)
    public boolean EmailExist(String email) {
        // 이메일이 null 인 경우
        if(email == null){
            return false;
        }

        // DB 에 이메일이 존재하는가? 존재하면 true, 존재하지 않으면 false 반환
        return memberRepository.existsByEmail(email);
    }

    // 입력받은 아이디가 존재하는가? (비밀번호 찾기)
    public boolean find_pw_IdAuth(String id, HttpSession session) {
        if(id == null){
            return false;
        }

        // DB 에 입력받은 아이디를 가진 유저가 존재하는가? 존재하면 true, 존재하지 않으면 false 반환
        if(memberRepository.existsById(id)){
            // 비밀번호 찾기에서 아이디를 입력하여 DB 에서 조회한 경우
            // 비밀번호 찾기 - 이메일 인증 페이지에서 사용
            session.setAttribute("IdAuth", id);
            return true;
        }
        else{return false;}
    }

    // 입력받은 아이디로 이메일 주소 찾음
    public String findEmail(String id) {
        return memberRepository.findEmailById(id);
    }

    // 유저의 권한 가져오기
    public String find_role(String id){
        return memberRepository.findRoleById(id);
    }

    // 유저의 플랜 정보 가져오기
    public String find_plan(String id){ return memberRepository.findPlanById(id);}

    // 아이디로 유저 정보 가져오기
    public Member find_user(String id){
        return memberRepository.findById(id).orElse(null);
    }
    
    // 일일 콘텐츠 생성 가능 횟수 가져오기
    public Integer GetContentsCount(String userId){
        return memberRepository.findContentsCount(userId);
    }

    // 일일 콘텐츠 생성 가능 횟수 차감
    @Transactional
    public String decreaseCount(String userId){
        Member member = find_user(userId);

        if((member != null)){
            // 현재 남은 횟수
            Integer currentCount = member.getContents_count();

            // 남은 횟수가 최소 1회 이상인 경우에만
            if(currentCount > 0){
                Member memberDecreaseCount = member.toBuilder()
                        .contents_count(currentCount - 1)
                        .build();
                memberRepository.save(memberDecreaseCount);
                return "Success";
            }
            else{
                return "콘텐츠 생성 불가능(잔여 횟수: 0)";
            }
        }
        else{
            return "유저 정보 없음";
        }
    }

    // free plan 을 이용하는 유저의 잔여 횟수를 10으로 초기화시킴
    @Transactional
    public int free_AutoInit(){
        return memberRepository.free_init();
    }

    // 만료일이 7일 남은 유저 리스트 가져오기
    public List<Member> find_ExpiryMember(){
        LocalDate targetDate = LocalDate.now().plusDays(7);
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(LocalTime.MAX);

        return memberRepository.findMembersExpiringBetween(startOfDay, endOfDay);
    }
    
    @Transactional
    public void CheckPlan(String userId){
        // 로그인한 사용자의 요금제가 plan 인 경우에만 검사
        if(find_plan(userId).equals("premium")){
            // 플랜 만료일 가져오기
            LocalDateTime EndDate = memberRepository.findEndDateById(userId);
            // 현재 시간 가져오기
            LocalDateTime now = LocalDateTime.now();

            if (EndDate.isBefore(now)) {
                // 플랜이 만료됨
                log.info("플랜이 만료되었습니다.");
                int result = memberRepository.handleExpiredPlan(userId);
                if(result == 1){
                    log.info("플랜 만료 처리됨(premium -> free)");
                }
                else{
                    log.info("플랜 만료 처리 오류");
                }

            } else {
                // 아직 플랜이 유효함
                log.info("플랜이 아직 유효합니다.");
            }
        }
    }

    // 플랜 만료날짜 계산
    public LocalDateTime cal_subscription_end_date(Member userInfo) {
        String currentPlan = userInfo.getPlan();

        // free -> premium 으로 변경하는 경우
        if(currentPlan.equals("free")){
            LocalDateTime now = LocalDateTime.now();
            // 한 달 뒤 날짜 리턴
            return now.plusMonths(1);
        }
        // 유효기간 연장을 하는 경우(premium -> premium)
        else {

            // 플랜 만료일 가져오기
            LocalDateTime end_date = userInfo.getSubscription_end_date();

            // 한 달 뒤 날짜 리턴
            return end_date.plusMonths(1);
        }
    }
    
    @Transactional
    public Member updatePlan(OrdersDto ordersDto) {
        Member member = find_user(ordersDto.getMemberid());
        
        // 기존 요금제가 free 인 경우(premium 가입)
        if(member.getPlan().equals("free")){

            Member planUpdate = member.toBuilder()
                    .contents_count(100)
                    .plan("premium")
                    .subscription_end_date(ordersDto.getMpaydate().plusMonths(1))
                    .build();

            return memberRepository.save(planUpdate);
        }
        
        // 기존 요금제가 premium 인 경우(유효기간 연장)
        else if(member.getPlan().equals("premium")){

            Member planUpdate = member.toBuilder()
                    .contents_count(100)
                    .subscription_end_date(member.getSubscription_end_date().plusMonths(1))
                    .build();

            return memberRepository.save(planUpdate);
        }
        else{
            return null;
        }
    }
}
