package com.example.capstone.service;

import com.example.capstone.dto.RespondDto;
import com.example.capstone.entity.Inquiry;
import com.example.capstone.entity.Member;
import com.example.capstone.entity.Respond;
import com.example.capstone.repository.InquiryRepository;
import com.example.capstone.repository.MemberRepository;
import com.example.capstone.repository.RespondRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RespondService {
    @Autowired
    private RespondRepository respondRepository;

    @Autowired
    private MemberService memberService;

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private MemberRepository memberRepository;

    // 댓글 조회
    public List<RespondDto> show_respond(Long inquiryId) {

        //결과 반환
        return respondRepository.findRespondByInquiryId(inquiryId)
                .stream()
                .map(RespondDto::creatRespondDto)
                .collect(Collectors.toList());
    }

    // 댓글 작성
    @Transactional
    public RespondDto create(RespondDto dto, Long inquiryId) throws IOException {
        if(dto == null){
            throw new IOException("댓글 정보 없음");
        }
        String memberId = memberService.ReturnSessionUser("id");

        //게시글 조회 및 예외 발생
        Inquiry inquiry = inquiryRepository.findById(inquiryId).orElseThrow(()
                ->new IllegalArgumentException("댓글 생성 실패!" + "대상 게시글이 없습니다."));

        // 멤버 조회 및 예외 발생
        Member member = memberRepository.findById(memberId).orElseThrow(()
                ->new IllegalArgumentException("댓글 생성 실패!" + "대상 회원이 없습니다."));
        
        
        // DB 에 저장할 respond 객체
        Respond respond;

        // 댓글 엔티티 생성
        respond = Respond.createRespond(dto, inquiry, member);

        //댓글 엔티티를 DB에 저장
        Respond created = respondRepository.save(respond);

        // 답변이 달리면 답변 여부를 'Y' 로 변경
        inquiryRepository.updateRespondCk("Y", inquiryId);

        // DTO 로 변환해 반환
        return RespondDto.creatRespondDto(created);
    }

    // 댓글 수정
    @Transactional
    public String update(Long id, RespondDto dto) {

        //댓글 조회 및 예외 발생
        Respond target = respondRepository.findById(id).orElse(null);

        if(target == null){
            return "대상 댓글이 존재하지 않습니다";
        }

        // 현재 로그인한 사용자 ID 가져오기
        String LoginId = memberService.ReturnSessionUser("id");

        // 로그인한 사용자의 권한 가져오기
        String LoginUserRole = memberService.find_role(LoginId);

        // 로그인한 사용자가 관리자가 아닌 일반 유저인 경우
        if(!LoginUserRole.equals("ROLE_ADMIN")){
            return "권한이 없습니다";
        }

        //댓글 수정
        target.patch(dto);

        //DB로 갱신
        respondRepository.save(target);

        return "Success";
    }

    // 댓글 삭제
    @Transactional
    public String delete(Long respondId) {

        // 현재 로그인한 사용자 ID 가져오기
        String LoginId = memberService.ReturnSessionUser("id");

        // 로그인한 사용자의 권한 가져오기
        String LoginUserRole = memberService.find_role(LoginId);

        // 로그인한 사용자가 관리자가 아닌 일반 유저인 경우
        if(!LoginUserRole.equals("ROLE_ADMIN")){
            return "권한이 없습니다";
        }

        try {
            respondRepository.deleteById(respondId);
        } catch (EmptyResultDataAccessException e) {
            // 해당 ID로 엔티티가 없을 때 예외 처리
            return "해당 ID에 대한 엔티티가 존재하지 않습니다: " + respondId;
            // 추가적인 처리 (예: 사용자에게 알림, 로깅 등)
        } catch (Exception e) {
            // 다른 예외 처리
            return "알 수 없는 오류 발생: " + e.getMessage();
            // 예외 로깅 및 처리
        }
        return "Success";

//        //댓글 조회 및 예외 발생
//        Respond target = respondRepository.findById(respondId).orElse(null);
//
//        if(target == null){
//            return "대상 댓글이 존재하지 않습니다";
//        }
//        else{
//            //댓글 삭제
//            respondRepository.delete(target);
//            // 답변이 삭제되면 답변 여부를 'N' 로 변경
//            inquiryRepository.updateRespondCk("N", target.getInquiry().getId());
//            return "Success";
//        }
    }
}
