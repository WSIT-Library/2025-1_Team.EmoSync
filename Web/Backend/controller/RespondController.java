package com.example.capstone.controller;

import com.example.capstone.dto.RespondDto;
import com.example.capstone.service.EmailService;
import com.example.capstone.service.InquiryService;
import com.example.capstone.service.RespondService;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@Slf4j
public class RespondController {
    @Autowired
    private RespondService respondService;
    
    @Autowired
    private InquiryService inquiryService;
    
    @Autowired
    private EmailService emailService;

    //댓글 조회
    @GetMapping("/respond/show/{inquiryId}")
    public ResponseEntity<List<RespondDto>> show(@PathVariable Long inquiryId) {
        //서비스에 위임
        List<RespondDto> respond = respondService.show_respond(inquiryId);
        //결과 응답
        return ResponseEntity.status(HttpStatus.OK).body(respond);
    }

    // 댓글 생성
    @PostMapping("/respond/create/{inquiryId}")
    public ResponseEntity<RespondDto> createParentComments(@PathVariable Long inquiryId, @RequestBody RespondDto dto) throws IOException, MessagingException {
        //서비스에 위임
        RespondDto createdDto = respondService.create(dto, inquiryId);

        // 답변이 DB에 등록된다면
        if(createdDto != null) {
            // 문의글 작성자에게 이메일로 알림을 보냄
            emailService.sendRespondNotification(dto.getInquiryWriterId(), createdDto.getBody());
        }
        //결과 응답
        return ResponseEntity.status(HttpStatus.OK).body(createdDto);
    }

    //댓글 수정
    @PatchMapping("/respond/update/{id}")
    public ResponseEntity<String> update(@PathVariable Long id, @RequestBody RespondDto dto) {
        //서비스에 위임
        String result = respondService.update(id, dto);

        // 댓글 수정이 실패한 경우
        if(!result.equals("Success")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }

        //결과 응답
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    //댓글 삭제
    @DeleteMapping("/respond/delete/{inquiryId}/{respondId}")
    public ResponseEntity<String> delete(@PathVariable Long respondId, @PathVariable Long inquiryId) {

        //서비스에 위임
        String result = respondService.delete(respondId);

        // 댓글 삭제가 실패한 경우
        if(!result.equals("Success")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
        // 삭제가 성공한 경우
        else{
            // 답변 삭제 후 문의글에 답변 여부를 'N' 으로 변경함
            String res_stat_ck = inquiryService.change_respond_stat(inquiryId);
            // 답변 여부 변경이 성공하면
            if(res_stat_ck.equals("Success")) {
                //결과 응답
                return ResponseEntity.status(HttpStatus.OK).body(res_stat_ck);
            }
            else{
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(res_stat_ck);
            }
        }
        
    }
}
