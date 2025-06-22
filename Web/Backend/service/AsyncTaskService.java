package com.example.capstone.service;

import com.example.capstone.dto.NotificationDto;
import com.example.capstone.entity.Contents;
import com.example.capstone.util.AsyncTaskCompletedEvent;
import com.example.capstone.util.ByteArrayMultipartFile;
import com.example.capstone.util.Util;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class AsyncTaskService {
    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private ContentsService contentsService;

    @Autowired
    private MemberService memberService;
    
    @Autowired
    private NotificationService notificationService;

    @Value("${app.FileRoot}")
    private String FILE_ROOT;

    // 콘텐츠 초기 생성 비동기
    @Async
    public void First_Generate_AsyncTask(byte[] imageBytes, byte[] audioBytes, String UserImage_name, String UserImage_Type,
                                         String UserAudio_name, String UserAudio_Type, String userId, HttpSession session) {
        log.info("초기 생성 비동기 작업이 시작되었습니다.");
        
        // 비동기 작업이 진행 중인지 확인하는 세션 변수
        session.setAttribute("AsyncTask", "working");
        // 생성 요청 시간
        LocalDateTime request_now = LocalDateTime.now();

        // 생성 시작 알림 저장
        NotificationDto start_notification = NotificationDto.builder()
                .receiver_id(userId)
                .notification_msg("none")
                .related_url("none")
                .is_success("생성 중")
                .failure_msg("x")
                .request_date(request_now)
                .build();

        // 임시로 저장된 알림 가져옴
        NotificationDto temp_notification = notificationService.start_gen_notification(start_notification);

        if(temp_notification == null) {
            session.removeAttribute("AsyncTask");
            throw new RuntimeException("알림을 수신할 유저가 없습니다.");
        }

        // 유저 이미지 상대 경로
        final String img_relative_path = "upload/client/image/";

        // 유저 음성 상대 경로
        final String audio_relative_path = "upload/client/audio/";
        
        try {
            // byte[]를 ByteArrayMultipartFile 로 래핑
            ByteArrayMultipartFile imageMultipartFile = new ByteArrayMultipartFile(imageBytes, UserImage_name, UserImage_Type);
            ByteArrayMultipartFile audioMultipartFile = new ByteArrayMultipartFile(audioBytes, UserAudio_name, UserAudio_Type);
            
            // 잔여 횟수 가져오기
            Integer remainingAttempts = memberService.GetContentsCount(userId);
            // 잔여 횟수가 없는 경우
            if (remainingAttempts < 1) {
                throw new RuntimeException("일일 생성 가능한 횟수를 모두 소진하셨습니다.");
            }
            if (imageMultipartFile.isEmpty()) {
                throw new RuntimeException("사용자의 얼굴 사진이 존재하지 않습니다.");
            }
            if (audioMultipartFile.isEmpty()) {
                throw new RuntimeException("사용자의 음성 파일이 존재하지 않습니다.");
            }
            
            // 유저 이미지, 음성 파일의 이름
            String img_originalFileName = imageMultipartFile.getOriginalFilename();
            String audio_originalFileName = audioMultipartFile.getOriginalFilename();

            if (img_originalFileName == null || audio_originalFileName == null) {
                throw new RuntimeException("이미지 또는 오디오 파일의 이름이 존재하지 않습니다.");
            }
            // 유저 이미지, 음성 파일의 확장자
            String img_fileExtension = img_originalFileName.substring(img_originalFileName.lastIndexOf("."));
            String audio_fileExtension = audio_originalFileName.substring(audio_originalFileName.lastIndexOf("."));
            // 랜덤한 값 생성
            String randomUUID = UUID.randomUUID().toString();
            // 업로드 된 파일이 중복될 수 있어서 파일 이름 재설정
            String img_reFileName =  randomUUID + img_fileExtension;
            String audio_reFileName = randomUUID + audio_fileExtension;

            // 업로드 경로에 파일명을 변경하여 저장
            if(!contentsService.UserFileUpload(imageMultipartFile, audioMultipartFile, img_relative_path, img_reFileName, img_fileExtension,
                    audio_relative_path, audio_reFileName, audio_fileExtension)) {
                throw new RuntimeException("유저 파일 업로드 실패");
            }

            JSONObject dbSrvJson = new JSONObject();
            dbSrvJson.put("img_reFileName", img_reFileName);
            dbSrvJson.put("audio_reFileName", audio_reFileName);
            dbSrvJson.put("filePath_root", FILE_ROOT);
            dbSrvJson.put("img_relative_path", img_relative_path);
            dbSrvJson.put("audio_relative_path", audio_relative_path);

            // 초기 생성시 이미지, 음악은 모두 생성함
            dbSrvJson.put("generate_opt", "all");
            // 초기 생성인지, 재생성인지 구분
            dbSrvJson.put("FirstOrRetry", "first");
            // 재생성시 사용할 감정값(초기 생성은 none)
            dbSrvJson.put("saved_emotion", "none");

            JSONObject result = contentsService.byPass("https://gently-artistic-magpie.ngrok-free.app/generatedContents", dbSrvJson, "POST");

            log.info("flask 반환값: {}",result.toString());

            // 만약 DeepFace 에서 얼굴을 인식하지 못한 경우
            if(result.getString("emotion").equals("No face detected")){
                // 사용자에게 입력받은 사진, 오디오 파일 삭제
                String del_userImg = contentsService.fileDelete(img_relative_path, img_reFileName);
                String del_userAudio = contentsService.fileDelete(audio_relative_path, audio_reFileName);

                // 사진 파일 삭제가 정상적으로 이뤄지지 않은 경우
                if(!del_userImg.equals("Success")) {
                    throw new RuntimeException(del_userImg);
                }

                // 오디오 파일 삭제가 정상적으로 이뤄지지 않은 경우
                if(!del_userAudio.equals("Success")) {
                    throw new RuntimeException(del_userAudio);
                }

                throw new RuntimeException("얼굴 인식에 실패했습니다. 다시 시도해주세요.");
            }
            else if(result.getString("emotion").equals("file upload fail")){
                // 사용자에게 입력받은 사진 파일 삭제
                String del_userImg = contentsService.fileDelete(img_relative_path, img_reFileName);

                // 파일 삭제가 정상적으로 이뤄지지 않은 경우
                if(!del_userImg.equals("Success")) {
                    throw new RuntimeException(del_userImg);
                }

                throw new RuntimeException("파일 업로드가 실패하였습니다. 다시 시도해주세요.");
            }
            else{
                // DB에 저장
                Contents saved = contentsService.saved_contents(userId, img_reFileName, audio_reFileName, result.getString("generatedImageName"), result.getString("generatedMusicName"),
                        img_relative_path, audio_relative_path, result.getString("generatedImagePath"), result.getString("generatedMusicPath"),
                        result.getString("emotion"), result.getString("emotionType"),
                        result.getString("emotion_detail"), result.getString("emotionType_detail"));

                // DB에 저장 실패한 경우
                if(saved == null) {
                    throw new RuntimeException("콘텐츠 저장에 실패하였습니다.");
                }

                // 일일 콘텐츠 생성 가능 횟수 차감
                String decreaseCount_result = memberService.decreaseCount(userId);
                if(!decreaseCount_result.equals("Success")){
                    throw new RuntimeException(decreaseCount_result);
                }

                // 리턴할 알림값 생성
                NotificationDto success_result = temp_notification.toBuilder()
                        .receiver_id(userId)
                        .notification_msg("콘텐츠 생성 완료(클릭하여 결과 확인)")
                        .related_url("/pages/contents/generated_result/"+saved.getId())
                        .is_success("성공")
                        .failure_msg("x")
                        .request_date(request_now)
                        .build();

                // 비동기 작업이 끝나서 세션 변수 삭제
                session.removeAttribute("AsyncTask");
                // 비동기 작업 완료 후 이벤트 발행
                publisher.publishEvent(new AsyncTaskCompletedEvent(this, success_result));
            }

        }catch (Exception e) {
            log.info("예외 타입: {}", e.getClass().getName());
            log.info("예외 메시지: {}", e.getMessage());
            log.info("예외 toString(): {}", e.toString());
            log.info("원인 예외: {}", String.valueOf(e.getCause()));
            // 리턴할 알림값 생성
            NotificationDto failure_result = temp_notification.toBuilder()
                    .receiver_id(userId)
                    .notification_msg("콘텐츠 생성 실패(클릭하여 원인 확인)")
                    .related_url("/pages/contents/notification_list")
                    .is_success("실패")
                    .failure_msg(e.getMessage())
                    .request_date(request_now)
                    .build();

            // 비동기 작업이 끝나서 세션 변수 삭제
            session.removeAttribute("AsyncTask");
            publisher.publishEvent(new AsyncTaskCompletedEvent(this, failure_result));
        }
    }

    // 콘텐츠 다시 생성 비동기
    @Async
    public void Retry_Generate_AsyncTask(Long contents_id, String generate_opt, String userId, HttpSession session) {
        log.info("다시 생성 비동기 작업이 시작되었습니다.");

        // 비동기 작업이 진행 중인지 확인하는 세션 변수
        session.setAttribute("AsyncTask", "working");

        // 생성 요청 시간
        LocalDateTime request_now = LocalDateTime.now();


        try {
            // 잔여 횟수 가져오기
            Integer remainingAttempts = memberService.GetContentsCount(userId);
            // 잔여 횟수가 없는 경우
            if (remainingAttempts < 1) {
                throw new RuntimeException("일일 생성 가능한 횟수를 모두 소진하셨습니다.");
            }

            // 콘텐츠 다시 생성이기 때문에 기존에 업로드된 사용자 표정 이미지는 그대로 사용됨
            Contents contents = contentsService.show(contents_id);

            JSONObject dbSrvJson = new JSONObject();
            dbSrvJson.put("img_reFileName", contents.getUserImgName());
            dbSrvJson.put("audio_reFileName", contents.getUserVoiceName());
            dbSrvJson.put("filePath_root", FILE_ROOT);
            dbSrvJson.put("img_relative_path", contents.getUserImgPath());
            dbSrvJson.put("audio_relative_path", contents.getUserVoicePath());

            // 다시 생성할 때는 특정 항목만 다시 생성 가능
            dbSrvJson.put("generate_opt", generate_opt);
            // 초기 생성인지, 재생성인지 구분
            dbSrvJson.put("FirstOrRetry", "retry");
            // 재생성시 사용할 감정값
            dbSrvJson.put("saved_emotion", contents.getEmotion());

            JSONObject result = contentsService.byPass("https://gently-artistic-magpie.ngrok-free.app/generatedContents", dbSrvJson, "POST");
            log.info("반환 결과: {}", result.toString());

            // 이미지, 음악 파일들 경로 저장할 변수 선언
            String gen_imgPath, gen_musicPath;

            // DB에 update 하고 반환된 값 저장할 변수 선언
            Contents saved;

            // DB에 update
            String del_image, del_music; // 파일 삭제 결과 저장할 변수
            if(generate_opt.equals("image")) {
                // 기존에 저장된 이미지 파일 삭제
                del_image = contentsService.fileDelete(contents.getGeneratedImgPath(), contents.getGeneratedImg());

                // 파일 삭제가 정상적으로 이뤄지지 않은 경우
                if(!del_image.equals("Success")) {
                    throw new RuntimeException(del_image);
                }

                // 재생성된 이미지 경로 저장
                String gen_img = result.getString("generatedImageName");
                gen_imgPath = result.getString("generatedImagePath");

                // gcs 에 파일 업로드를 실패한 경우
                if(gen_img.equals("null")) {
                    throw new RuntimeException("파일 업로드가 실패하였습니다. 다시 시도해주세요.");
                }
                // 다시 생성한 항목이 이미지인 경우
                saved = contentsService.update_contents(contents_id, generate_opt, gen_img, "none", gen_imgPath, "none");
            }
            else if(generate_opt.equals("music")) {
                // 기존에 저장된 음악 파일 삭제
                del_music = contentsService.fileDelete(contents.getGeneratedMusicPath() ,contents.getGeneratedMusic());

                // 파일 삭제가 정상적으로 이뤄지지 않은 경우
                if(!del_music.equals("Success")) {
                    throw new RuntimeException(del_music);
                }

                // 재생성된 음악 이름
                String gen_music =  result.getString("generatedMusicName");

                // 재생성된 음악 경로
                gen_musicPath = result.getString("generatedMusicPath");

                // gcs 에 파일 업로드를 실패한 경우
                if(gen_music.equals("null")) {
                    throw new RuntimeException("파일 업로드가 실패하였습니다. 다시 시도해주세요.");
                }

                // 다시 생성한 항목이 음악인 경우
                saved = contentsService.update_contents(contents_id, generate_opt, "none", gen_music, "none", gen_musicPath);
            }
            else{
                // 기존에 저장된 이미지, 음악 파일 삭제
                del_image = contentsService.fileDelete(contents.getGeneratedImgPath(), contents.getGeneratedImg());
                del_music = contentsService.fileDelete(contents.getGeneratedMusicPath() ,contents.getGeneratedMusic());

                // 이미지 파일 삭제가 정상적으로 이뤄지지 않은 경우
                if(!del_image.equals("Success")) {
                    throw new RuntimeException(del_image);
                }

                // 음악 파일 삭제가 정상적으로 이뤄지지 않은 경우
                if(!del_music.equals("Success")) {
                    throw new RuntimeException(del_music);
                }

                // 재생성한 이미지, 음악 이름과 경로
                String gen_img = result.getString("generatedImageName");
                gen_imgPath = result.getString("generatedImagePath");
                String gen_music =  result.getString("generatedMusicName");
                gen_musicPath = result.getString("generatedMusicPath");

                // gcs 에 파일 업로드를 실패한 경우
                if(gen_img.equals("null") || gen_music.equals("null")) {
                    throw new RuntimeException("파일 업로드가 실패하였습니다. 다시 시도해주세요.");
                }

                // 전부 다시 생성한 경우
                saved = contentsService.update_contents(contents_id, generate_opt, gen_img, gen_music, gen_imgPath, gen_musicPath);
            }

            // DB에 저장 실패한 경우
            if(saved == null) {
                throw new RuntimeException("콘텐츠 저장에 실패하였습니다.");
            }

            // 일일 콘텐츠 생성 가능 횟수 차감
            String decreaseCount_result = memberService.decreaseCount(userId);
            if(!decreaseCount_result.equals("Success")){
                throw new RuntimeException(decreaseCount_result);
            }

            // 리턴할 알림값 생성
            NotificationDto success_result = NotificationDto.builder()
                    .receiver_id(userId)
                    .notification_msg("콘텐츠 생성 완료(클릭하여 결과 확인)")
                    .related_url("/pages/contents/generated_result/"+saved.getId())
                    .is_success("성공")
                    .failure_msg("x")
                    .request_date(request_now)
                    .build();

            // 비동기 작업이 끝나서 세션 변수 삭제
            session.removeAttribute("AsyncTask");

            // 비동기 작업 완료 후 이벤트 발행
            publisher.publishEvent(new AsyncTaskCompletedEvent(this, success_result));
        }catch (Exception e) {
            log.info("예외 타입: {}", e.getClass().getName());
            log.info("예외 메시지: {}", e.getMessage());
            log.info("예외 toString(): {}", e.toString());
            log.info("원인 예외: {}", String.valueOf(e.getCause()));
            // 리턴할 알림값 생성
            NotificationDto failure_result = NotificationDto.builder()
                    .receiver_id(userId)
                    .notification_msg("콘텐츠 생성 실패(클릭하여 원인 확인)")
                    .related_url("/pages/contents/notification_list")
                    .is_success("실패")
                    .failure_msg(e.getMessage())
                    .request_date(request_now)
                    .build();

            // 비동기 작업이 끝나서 세션 변수 삭제
            session.removeAttribute("AsyncTask");

            publisher.publishEvent(new AsyncTaskCompletedEvent(this, failure_result));
        }
    }
}
