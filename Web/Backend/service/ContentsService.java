package com.example.capstone.service;

import com.example.capstone.entity.Contents;
import com.example.capstone.entity.Member;
import com.example.capstone.repository.ContentsRepository;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentsService {
    
    @Autowired
    private MemberService memberService;

    @Autowired
    private ContentsRepository contentsRepository;

    // 버킷 이름
    @Value("${spring.cloud.gcp.storage.bucket}")
    private String bucketName;

    private final Storage storage;
    
    // 유저가 촬영한 얼굴 사진과 녹음한 음성 파일 업로드
    public boolean UserFileUpload(MultipartFile img_file, MultipartFile audio_file, String img_path, String img_reFileName, String img_fileExtension,
                                  String audio_path, String audio_reFileName, String audio_fileExtension) {

        System.err.println("이미지 업로드");

        try {
            // Cloud 에 이미지, 음성 파일 업로드
            BlobInfo blob_img = storage.create(
                    BlobInfo.newBuilder(bucketName, img_path + img_reFileName)
                            .setContentType(img_fileExtension)
                            .build(),
                    img_file.getBytes()
            );

            BlobInfo blob_audio = storage.create(
                    BlobInfo.newBuilder(bucketName, audio_path + audio_reFileName)
                            .setContentType(audio_fileExtension)
                            .build(),
                    audio_file.getBytes()
            );
            
            return true;
        }catch (Exception e) {
            log.error("FileUpload Exception [Err_Msg]: {}", e.getMessage());
            return false;
        }
    }

    // 스프링 <-> 파이썬의 http 통신을 위한 함수
    public JSONObject byPass(String url, JSONObject jsonData, String option) throws JSONException {
        JSONObject responseJson = new JSONObject();
        log.info("byPass 진입");
        try {
            // 연결할 url 생성
            URL start_object = new URL(url);
            log.info("CONNECT URL :" + url);

            // http 객체 생성
            HttpURLConnection start_con = (HttpURLConnection) start_object.openConnection();
            start_con.setDoOutput(true);
            start_con.setDoInput(true);

            // 설정 정보
            start_con.setRequestProperty("Content-Type", "application/json");
            start_con.setRequestProperty("Accept", "application/json");
            start_con.setRequestMethod(option);
            // 연결, 읽기 타임아웃을 100분으로 설정
            start_con.setConnectTimeout(6000000);
            start_con.setReadTimeout(6000000);

            // data 전달
            log.info("REQUEST DATA : " + jsonData);

            // 출력 부분
            OutputStreamWriter wr = new OutputStreamWriter(start_con.getOutputStream());
            wr.write(jsonData.toString());
            wr.flush();

            // 응답 받는 부분
            int start_HttpResult = start_con.getResponseCode();

            // 결과 성공일 경우 = HttpResult 200일 경우
            if (start_HttpResult == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(start_con.getInputStream(), StandardCharsets.UTF_8));

                String line = br.readLine();
                responseJson = new JSONObject(line);
                br.close();
                return responseJson;
            } else {
                // 그 외의 경우(실패)
                responseJson.put("result", "FAIL");
                return responseJson;
            }
        } catch (Exception e) {
            responseJson.put("result", "EXCEPTION");
            return responseJson;
        }
    }

    // 생성된 콘텐츠 DB 저장
    @Transactional
    public Contents saved_contents(String userId, String img_reFileName, String audio_reFileName, String gen_img, String gen_music,
                                   String img_relative_path,  String audio_relative_path, String gen_imgPath, String gen_musicPath,
                                   String emotion, String emotionType, String emotion_detail, String emotionType_detail) {

        // ID로 사용자 정보 가져오기
        Member member = memberService.find_user(userId);

        // 절대 경로에서 상대 경로로 변경
//        String User_uploadPath_mod = User_uploadPath.replaceFirst(".*/upload", "/upload");
//        String gen_imgPath_mod = gen_imgPath.replaceFirst(".*/upload", "/upload");
//        String gen_musicPath_mod = gen_musicPath.replaceFirst(".*/upload", "/upload");

        // entity 생성
        Contents NewContents = Contents.builder()
                .con_member(member)
                .userImgName(img_reFileName)
                .userVoiceName(audio_reFileName)
                .generatedImg(gen_img)
                .generatedMusic(gen_music)
                .userImgPath(img_relative_path)
                .userVoicePath(audio_relative_path)
                .generatedImgPath(gen_imgPath)
                .generatedMusicPath(gen_musicPath)
                .generatedDate(LocalDateTime.now())
                .emotion(emotion)
                .emotionType(emotionType)
                .emotionDetail(emotion_detail)
                .emotionTypeDetail(emotionType_detail)
                .build();

        return contentsRepository.save(NewContents);
    }

    // 다시 생성된 콘텐츠 DB update
    @Transactional
    public Contents update_contents(Long contents_id, String generate_opt, String gen_img, String gen_music, String gen_imgPath, String gen_musicPath) {
        
        // 저장된 contents 불러오기
        Contents saved_contents = show(contents_id);

        // 새로 update 할 contents 변수 선언
        Contents UpdateContents;

        // image 만 update
        if(generate_opt.equals("image")){
            UpdateContents = saved_contents.toBuilder()
                    .generatedImg(gen_img)
                    .generatedImgPath(gen_imgPath)
                    .generatedDate(LocalDateTime.now())
                    .build();
        }
        // music 만 update
        else if(generate_opt.equals("music")){
            UpdateContents = saved_contents.toBuilder()
                    .generatedMusic(gen_music)
                    .generatedMusicPath(gen_musicPath)
                    .generatedDate(LocalDateTime.now())
                    .build();
        }
        // 전부 다 update
        else{
            UpdateContents = saved_contents.toBuilder()
                    .generatedImg(gen_img)
                    .generatedImgPath(gen_imgPath)
                    .generatedMusic(gen_music)
                    .generatedMusicPath(gen_musicPath)
                    .generatedDate(LocalDateTime.now())
                    .build();
        }


        return contentsRepository.save(UpdateContents);
    }


    // 콘텐츠 ID 로 저장된 콘텐츠 가져오기
    public Contents show(Long contents_id){
        return contentsRepository.findById(contents_id).orElse(null);
    }
    
    // (page)사용자 ID + 특정 날짜에 저장된 콘텐츠 가져오기
    public Page<Contents> show_userId_date(String userId, Pageable pageable, LocalDateTime startDate, LocalDateTime endDate){
        return contentsRepository.findContentsByUserIdAndDate(pageable, userId, startDate, endDate);
    }

    // (List)사용자 ID 로 특정 날짜에 저장된 콘텐츠 가져오기
    public List<Contents> List_contents_date(String userId, LocalDateTime startDate, LocalDateTime endDate){
        return contentsRepository.findListByUserIdAndDate(userId, startDate, endDate);
    }


    // Bar Chart 에 들어갈 Data 값을 계산 후 리턴
    // 복합 감정 별 빈도 수 계산
    public List<Integer> cal_BarData_mixed(List<Contents> contentsList){
        // Bar Chart 에 들어갈 Data (0으로 37개 초기화)
        List<Integer> BarData_mixed = new ArrayList<>(Collections.nCopies(16, 0));

        // 감정 이름을 리스트로 보관하여 인덱스 매핑
        List<String> mixed_emotions = Arrays.asList(
                "excited",
                "content",
                "curious",
                "amused",
                "anxious",
                "nervous",
                "frustrated",
                "irritated",
                "melancholy",
                "tired",
                "bored",
                "shocked",
                "confused",
                "affectionate",
                "serious",
                "determined"
        );

        
        // 감정 리스트를 순회하며 해당 인덱스 값을 +1
        for (Contents content : contentsList) {
            String emotion = content.getEmotion();  // ex: "amused"
            int index = mixed_emotions.indexOf(emotion);  // 인덱스 찾기
            if (index != -1) {
                int current = BarData_mixed.get(index);
                BarData_mixed.set(index, current + 1);
            }
        }

        return BarData_mixed;
    }

    // 단일 감정 별 빈도 수 계산(low, middle, high)
    public List<Integer> calBarDataByLevel(List<Contents> contentsList, String levelPrefix) {
        List<Integer> barData = new ArrayList<>(Collections.nCopies(7, 0));

        List<String> baseEmotions = Arrays.asList(
                "happy", "neutral", "surprise", "disgusted", "sad", "angry", "fear"
        );

        // prefix 붙인 감정들 리스트 만들기
        List<String> targetEmotions = baseEmotions.stream()
                .map(emotion -> levelPrefix + "_" + emotion)
                .toList();

        for (Contents content : contentsList) {
            String emotion = content.getEmotion(); // ex: "low_happy"
            int index = targetEmotions.indexOf(emotion);
            if (index != -1) {
                barData.set(index, barData.get(index) + 1);
            }
        }

        return barData;
    }


    // Donut Chart 에 들어갈 Data 값을 계산 후 리턴
    // 각 감정 별 비율 계산
    public List<Integer> cal_DonutData(List<Contents> contentsList){

        // emotionType 별 빈도 수 계산
        List<Integer> emotionType_freq = Arrays.asList(0, 0, 0);
        
        for (Contents content : contentsList) {
            String emotionType = content.getEmotionType();
            switch (emotionType) {
                case "positive" -> emotionType_freq.set(0, emotionType_freq.get(0) + 1);
                case "negative" -> emotionType_freq.set(1, emotionType_freq.get(1) + 1);
                case "neutral" -> emotionType_freq.set(2, emotionType_freq.get(2) + 1);
                default -> log.info("예외");
            }
        }

        // emotionType 별 비율 계산
        List<Integer> DonutData = Arrays.asList(0, 0, 0);

        // 총합 구하기
        int sum = 0;
        for (int data : emotionType_freq) {
            sum += data;
        }

        int i = 0;
        for (int emotionType_data : emotionType_freq) {
            DonutData.set(i, Math.round((float) emotionType_data / sum * 100));
            i++;
        }

        return DonutData;
    }


    // upload 폴더에 있는 파일 삭제
    public String fileDelete(String filePath, String fileName) {
        boolean deleted = storage.delete(bucketName, filePath + fileName);
        if (!deleted) {
            return "GCS 파일 삭제 실패: 파일이 존재하지 않거나 권한 없음";
        }
        else{
            return "Success";
        }
    }

    // 콘텐츠 삭제
    public String deleteContents(Long contents_id) {
        Contents contents = contentsRepository.findById(contents_id).orElse(null);
        
        String userId = memberService.ReturnSessionUser("id");
        
        if(contents == null){
            return "콘텐츠가 존재하지 않습니다.";
        }

        // 일반 유저 중에서 콘텐츠 소유자와 로그인한 사용자가 다른 경우
        if((!contents.getCon_member().getId().equals(userId))&&(contents.getCon_member().getRole().equals("ROLE_USER"))){
            return "접근 권한이 없습니다.";
        }
        
        // 사용자가 업로드한 사진 삭제
        String userImage_result = fileDelete(contents.getUserImgPath(), contents.getUserImgName());
        if(!userImage_result.equals("Success")){
            return userImage_result;
        }

        // 사용자가 녹음한 음성 삭제
        String userAudio_result = fileDelete(contents.getUserVoicePath(), contents.getUserVoiceName());
        if(!userAudio_result.equals("Success")){
            return userAudio_result;
        }

        // 생성된 이미지 삭제
        String genImage_result = fileDelete(contents.getGeneratedImgPath(), contents.getGeneratedImg());
        if(!genImage_result.equals("Success")){
            return genImage_result;
        }

        // 생성된 음악 삭제
        String genMusic_result = fileDelete(contents.getGeneratedMusicPath(), contents.getGeneratedMusic());
        if(!genMusic_result.equals("Success")){
            return genMusic_result;
        }

        contentsRepository.delete(contents);
        return "Success";
    }

    // 감정 비율에 따른 감정 점수 계산
    public int cal_EmotionScore(List<Integer> donutData) {
        if (donutData == null || donutData.size() != 3) {
            throw new IllegalArgumentException("Input list must contain exactly three elements: [positive, negative, neutral]");
        }

        int positive = donutData.get(0);
        int negative = donutData.get(1);
        int neutral  = donutData.get(2);

        int total = positive + negative + neutral;
        if (total == 0) return 50; // 아무 감정도 없으면 중립 점수

        // 가중치 설정: Neutral 은 영향 없음
        double weightPositive = 1.0;
        double weightNeutral  = 0.0;
        double weightNegative = -1.0;

        double rawScore = (
                (positive * weightPositive) +
                        (neutral  * weightNeutral) +
                        (negative * weightNegative)
        ) / total * 100;

        double normalizedScore = (rawScore + 100) / 2.0;

        return (int) Math.round(normalizedScore);
    }

    // 감정 점수에 따른 감정 상태 리턴
    public String cal_EmotionState(int emotionScore) {
        if(emotionScore >= 0 && emotionScore <= 19){
            return "매우 나쁨";
        }
        else if(emotionScore >= 20 && emotionScore <= 39){
            return "약간 나쁨";
        }
        else if(emotionScore >= 40 && emotionScore <= 59){
            return "보통";
        }
        else if(emotionScore >= 60 && emotionScore <= 79){
            return "약간 좋음";
        }
        else if(emotionScore >= 80 && emotionScore <= 100){
            return "매우 좋음";
        }
        else{
            return "점수 오류";
        }
    }

    // 감정 점수에 따른 추천 문구 리턴
    public String cal_recommended_phrase(String EmotionState) {
        if(EmotionState.equals("매우 나쁨")){
            return "최근에 많이 힘드셨던 것 같아요. 잠시 멈춰 쉬어가도 괜찮습니다.";
        }
        else if(EmotionState.equals("약간 나쁨")){
            return "마음이 계속 무거우셨군요. 저희가 함께하겠습니다.";
        }
        else if(EmotionState.equals("보통")){
            return "감정의 흐름이 비교적 안정적이셨던 것 같아요. 평온한 시간이 계속되길 바랍니다.";
        }
        else if(EmotionState.equals("약간 좋음")){
            return "긍정적인 기분이 자주 느껴지셨던 것 같아요. 그런 순간들이 더욱 많아지길 바랍니다.";
        }
        else if(EmotionState.equals("매우 좋음")){
            return "좋은 감정이 꾸준히 이어지고 있네요! 당신의 일상에 더 많은 빛이 함께하길 바랍니다.";
        }
        else{
            return "상태 오류";
        }
    }
}
