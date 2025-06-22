package com.example.capstone.controller;

import com.example.capstone.entity.Contents;
import com.example.capstone.entity.Notification;
import com.example.capstone.service.AsyncTaskService;
import com.example.capstone.service.ContentsService;
import com.example.capstone.service.MemberService;
import com.example.capstone.service.NotificationService;
import com.example.capstone.util.Util;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Controller
@Slf4j
@RequiredArgsConstructor
public class ContentsController {
    @Autowired
    private MemberService memberService;
    
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ContentsService contentsService;

    @Autowired
    private AsyncTaskService asyncTaskService;

    @Value("${app.FileRoot}")
    private String FILE_ROOT;

    // 버킷 이름
    @Value("${spring.cloud.gcp.storage.bucket}")
    private String bucketName;

    private final Storage storage;

    // 공통으로 사용되는 model
    @ModelAttribute
    public void ShardedModel(Model model) {
        model.addAttribute("userId", memberService.ReturnSessionUser("id"));
        model.addAttribute("FileRoot", FILE_ROOT);
    }

    // 콘텐츠 생성 - 사용자 사진 촬영 페이지
    @GetMapping("/pages/contents/user_photo")
    public String user_photo(HttpSession session, Model model) {
        // 기존에 저장된 세션값(콘텐츠 ID) 제거
        session.removeAttribute("contents_id");

        // 잔여 횟수 가져오기
        Integer remainingAttempts = memberService.GetContentsCount(memberService.ReturnSessionUser("id"));
        model.addAttribute("remainingAttempts", remainingAttempts);
        return "pages/contents/user_photo";
    }
    
    // 사용자 사진 및 음성 재녹음 요청
    @PostMapping("/pages/contents/re_user_photo")
    public String re_user_photo(@RequestParam("contents_id") Long contents_id) {
        log.info("contents_id {}", contents_id);
        // 방금 생성된 콘텐츠 삭제
        if(contentsService.deleteContents(contents_id).equals("Success")){
            // 사용자 사진 및 음성 녹음 페이지로 리다이렉션
            return "redirect:/pages/contents/user_photo";
        }
        else{
            String msg = Util.url.encode("잘못된 콘텐츠 정보입니다.");
            return "redirect:/pages/contents/error/"+msg;
        }
    }

    // 에러 페이지
    @GetMapping("/pages/contents/error/{msg}")
    public String generated_error(@PathVariable String msg, Model model) {
        String error_message = msg.replace("+", " ");
        model.addAttribute("error_message", error_message);
        return "pages/contents/generated_error";
    }
    
    // 유저 이미지 업로드 & 파이썬 전송 & 생성된 결과물 DB 저장(콘텐츠 초기 생성)
    @PostMapping("/UserUpload")
    public ResponseEntity<String> contents_generate(@RequestParam("UserImage") MultipartFile UserImage,
                                                    @RequestParam("UserAudio") MultipartFile UserAudio,
                                                    @RequestParam("userId") String userId,
                                                    HttpSession session) throws IOException {

        // 현재 비동기 작업이 진행 중인지 확인
        String Is_AsyncTask = (String) session.getAttribute("AsyncTask");

        // 진행 중인 작업이 존재하지 않기 때문에 작업 가능
        if(Is_AsyncTask == null){
            // UserImage 와 UserAudio 를 byte[]로 변환
            // MultipartFile 은 한번 읽으면 스트림을 소모하기 때문에 계속 사용할 수 가 없음
            // 따라서 바이트 배열로 변환 후 클래스 래핑으로 MultipartFile 과 유사한 기능을 하도록 만듦
            byte[] imageBytes = UserImage.getBytes();
            byte[] audioBytes = UserAudio.getBytes();

            // 초기 생성 작업 비동기로 백그라운드에서 처리함
            asyncTaskService.First_Generate_AsyncTask(imageBytes, audioBytes, "captured-image.png", "image/png",
                    "record-voice.webm", "audio/webm", userId, session);
            return ResponseEntity.accepted().body("콘텐츠 초기 생성이 시작되었습니다.\n완료되면 Toast 알림 또는 생성 목록 메뉴에서 확인할 수 있습니다.");
        }
        // 이미 비동기 작업이 진행 중이기 때문에 작업 거부
        else if(Is_AsyncTask.equals("working")){
            return ResponseEntity.accepted().body("이미 콘텐츠 생성이 진행 중 입니다.\n생성이 완료된 후에 다시 시도해주세요.");
        }
        else{
            return ResponseEntity.accepted().body("생성 오류 발생\n다시 시도해주세요.");
        }
    }

    // 콘텐츠 다시 생성
    @PostMapping("/re_contents_generate")
    public ResponseEntity<String> re_contents_generate(@RequestParam("contents_id") Long contents_id,
                                                       @RequestParam("generate_opt") String generate_opt,
                                                       @RequestParam("userId") String userId,
                                                       HttpSession session) {

        // 현재 비동기 작업이 진행 중인지 확인
        String Is_AsyncTask = (String) session.getAttribute("AsyncTask");

        // 진행 중인 작업이 존재하지 않기 때문에 작업 가능
        if(Is_AsyncTask == null){
            // 다시 생성 작업 비동기로 백그라운드에서 처리함
            asyncTaskService.Retry_Generate_AsyncTask(contents_id, generate_opt, userId, session);
            return ResponseEntity.accepted().body("콘텐츠 다시 생성이 시작되었습니다.\n완료되면 Toast 알림 또는 생성 목록 메뉴에서 확인할 수 있습니다.");
        }
        // 이미 비동기 작업이 진행 중이기 때문에 작업 거부
        else if(Is_AsyncTask.equals("working")){
            return ResponseEntity.accepted().body("이미 콘텐츠 생성이 진행 중 입니다.\n생성이 완료된 후에 다시 시도해주세요.");
        }
        else{
            return ResponseEntity.accepted().body("생성 오류 발생\n다시 시도해주세요.");
        }
    }
    
    // 콘텐츠 생성 결과 페이지
    @GetMapping("/pages/contents/generated_result/{contents_id}")
    public String generated_result(@PathVariable Long contents_id, Model model, HttpSession session) {
        log.info("결과 페이지 리다이렉션 받음");

        Contents contents = contentsService.show(contents_id);

        if(contents == null){
            // 에러 페이지로 이동
            String msg = Util.url.encode("존재하지 않는 콘텐츠 입니다.");
            return "redirect:/pages/contents/error/"+msg;
        }
        model.addAttribute("contents", contents);

        // 잔여 횟수 가져오기
        Integer remainingAttempts = memberService.GetContentsCount(memberService.ReturnSessionUser("id"));
        model.addAttribute("remainingAttempts", remainingAttempts);
        return "pages/contents/generated_result";
    }
    

    // 저장된 콘텐츠 페이지
    @GetMapping("/pages/contents/contents_list")
    public String contents_list( @RequestParam(value = "startDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startDate,
                                    @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endDate,
                                      Model model, Pageable pageable) {

        // 현재 로그인한 사용자 ID
        String userId = memberService.ReturnSessionUser("id");

        // 현재 사용자의 플랜 정보
        String userPlan = memberService.find_plan(userId);

        // plan 이 free 인 유저는 접근 불가
        if(userPlan.equals("free")){
            String msg = Util.url.encode("해당 메뉴는 premium 요금제 전용 메뉴입니다. 사용하시려면 요금제를 업그레이드 해주세요.");
            return "redirect:/members/error/" + msg;
        }

        // page 번호 작업. 이 코드를 넣어야 주소에 page=1을 입력하면 첫번째 페이지로 이동함
        int page = (pageable.getPageNumber() == 0) ? 0:(pageable.getPageNumber() - 1); // page 는 index 처럼 0부터 시작
        // page 에 보여줄 콘텐츠 개수 지정
        pageable = PageRequest.of(page,1);
        
        Page<Contents> contentsList;
        
        // 조회 옵션(전체기간 or 특정기간)
        String search_opt;
        
        // 날짜 값이 들어있지 않다면 최근 1주일 조회
        if(startDate == null || endDate == null) {
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = end.minusWeeks(1);
            contentsList = contentsService.show_userId_date(userId, pageable, start, end);
            search_opt = "default";
        }
        // 날짜 값이 들어있다면 특정 기간 조회
        else {
            contentsList = contentsService.show_userId_date(userId, pageable, startDate, endDate);
            search_opt = "oneself";
        }
        
        model.addAttribute("contentsList", contentsList);
        model.addAttribute("search_opt", search_opt);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "pages/contents/contents_list";
    }

    // 콘텐츠 삭제
    @DeleteMapping("/pages/contents/delete/{contents_id}")
    public ResponseEntity<String> delete(@PathVariable Long contents_id) {

        //서비스에 위임
        String result = contentsService.deleteContents(contents_id);

        // 콘텐츠 삭제가 실패한 경우
        if(!result.equals("Success")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
        // 삭제가 성공한 경우
        else{
            // 답변 여부 변경이 성공하면
            return ResponseEntity.status(HttpStatus.OK).body(result);
        }
    }

    // 콘텐츠 다운로드
    @GetMapping("/pages/contents/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam String filePath) {
        Blob blob = storage.get(BlobId.of(bucketName, filePath));
        ByteArrayResource resource = new ByteArrayResource(blob.getContent());

        // 1. 확장자 추출
        String originalName = blob.getName(); // 예: images/example.png
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex != -1) {
            extension = originalName.substring(dotIndex); // ".png"
        }

        // 2. 새 이름 만들기
        String customName = "downloaded-image" + extension;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + customName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // 감정 통계 페이지
    @GetMapping("/pages/contents/emotion_stats")
    public String emotion_stats(@RequestParam(value = "startDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startDate,
                                @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endDate,
                                Model model) {

        // 현재 로그인한 사용자 ID
        String userId = memberService.ReturnSessionUser("id");

        // 현재 사용자의 플랜 정보
        String userPlan = memberService.find_plan(userId);

        // plan 이 free 인 유저는 접근 불가
        if(userPlan.equals("free")){
            String msg = Util.url.encode("해당 메뉴는 premium 요금제 전용 메뉴입니다. 사용하시려면 요금제를 업그레이드 해주세요.");
            return "redirect:/members/error/" + msg;
        }
        
        // 콘텐츠
        List<Contents> contentsList;

        // 조회 옵션(전체기간 or 특정기간)
        String search_opt;

        // 날짜 값이 들어있지 않다면 최근 1주일 조회
        if(startDate == null || endDate == null) {
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = end.minusWeeks(1);
            contentsList = contentsService.List_contents_date(userId, start, end);
            search_opt = "default";
        }
        // 날짜 값이 들어있다면 특정 기간 조회
        else {
            contentsList = contentsService.List_contents_date(userId, startDate, endDate);
            search_opt = "oneself";
        }

        // 저장된 콘텐츠가 없다면 model 변수에 null 저장
        if(contentsList.isEmpty()) {

            model.addAttribute("EmotionScore", null);
            model.addAttribute("EmotionState", null);
            model.addAttribute("BarData_mixed1", null);
            model.addAttribute("BarData_mixed2", null);
            model.addAttribute("BarData_low", null);
            model.addAttribute("BarData_middle", null);
            model.addAttribute("BarData_high", null);
            model.addAttribute("DonutData", null);
            model.addAttribute("search_opt", search_opt);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
        }
        else{
            // Bar Chart 에 들어갈 Data 계산(복합 감정 별 빈도수)
            List<Integer> BarData_mixed = contentsService.cal_BarData_mixed(contentsList);

            // 리스트의 중간 인덱스를 기준으로 두 부분으로 나눕니다.
            int mid = BarData_mixed.size() / 2;

            List<Integer> BarData_mixed1 = BarData_mixed.subList(0, mid);
            List<Integer> BarData_mixed2 = BarData_mixed.subList(mid, BarData_mixed.size());

            // Bar Chart 에 들어갈 Data 계산(단일 감정 중 low 의 빈도수)
            List<Integer> BarData_low = contentsService.calBarDataByLevel(contentsList, "low");

            // Bar Chart 에 들어갈 Data 계산(단일 감정 중 middle 의 빈도수)
            List<Integer> BarData_middle = contentsService.calBarDataByLevel(contentsList, "middle");

            // Bar Chart 에 들어갈 Data 계산(단일 감정 중 high 의 빈도수)
            List<Integer> BarData_high = contentsService.calBarDataByLevel(contentsList, "high");

            // Donut Chart 에 들어갈 Data 계산(감정 별 비율(퍼센트))
            List<Integer> DonutData = contentsService.cal_DonutData(contentsList);

            // 감정 점수
            int EmotionScore = contentsService.cal_EmotionScore(DonutData);

            // 감정 점수에 따른 상태
            String EmotionState = contentsService.cal_EmotionState(EmotionScore);

            // 감정 상태에 따른 추천 문구
            String recommended_phrase = contentsService.cal_recommended_phrase(EmotionState);

            model.addAttribute("EmotionScore", EmotionScore);
            model.addAttribute("EmotionState", EmotionState);
            model.addAttribute("recommended_phrase", recommended_phrase);
            model.addAttribute("BarData_mixed1", BarData_mixed1);
            model.addAttribute("BarData_mixed2", BarData_mixed2);
            model.addAttribute("BarData_low", BarData_low);
            model.addAttribute("BarData_middle", BarData_middle);
            model.addAttribute("BarData_high", BarData_high);
            model.addAttribute("DonutData", DonutData);
            model.addAttribute("search_opt", search_opt);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
        }
        return "pages/contents/emotion_stats";
    }

    // 콘텐츠 생성 알림 목록 페이지
    @GetMapping("/pages/contents/notification_list")
    public String notification_list(Pageable pageable, Model model){
        String userId = memberService.ReturnSessionUser("id");
        
        // 콘텐트 생성 알림 가져옴
        Page<Notification> notifications = notificationService.All_notification(pageable, userId);
        model.addAttribute("notifications", notifications);
        return "pages/contents/notification_list";
    }

    // 알림 삭제
    @DeleteMapping("/pages/contents/notification_delete/{notification_id}")
    public ResponseEntity<String> notification_delete(@PathVariable Long notification_id) {

        //서비스에 위임
        String result = notificationService.delete_notification(notification_id);

        // 콘텐츠 삭제가 실패한 경우
        if(!result.equals("Success")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
        // 삭제가 성공한 경우
        else{
            // 답변 여부 변경이 성공하면
            return ResponseEntity.status(HttpStatus.OK).body(result);
        }
    }
}
