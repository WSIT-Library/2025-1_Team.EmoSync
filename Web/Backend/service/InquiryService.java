package com.example.capstone.service;

import com.example.capstone.dto.InquiryDto;
import com.example.capstone.entity.Inquiry;
import com.example.capstone.entity.Member;
import com.example.capstone.entity.Respond;
import com.example.capstone.repository.InquiryRepository;
import com.example.capstone.repository.MemberRepository;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryService {
    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private MemberRepository memberRepository;

    // 버킷 이름
    @Value("${spring.cloud.gcp.storage.bucket}")
    private String bucketName;

    private final Storage storage;

    //저장된 글 리스트 불러오기
    @Transactional(readOnly = true)
    public Page<Inquiry> get_all_inquiry(Pageable pageable) {
        // page 번호 작업. 이 코드를 넣어야 주소에 page=1을 입력하면 첫번째 페이지로 이동함
        int page = (pageable.getPageNumber() == 0) ? 0:(pageable.getPageNumber() - 1); // page 는 index 처럼 0부터 시작
        // page 에 보여줄 게시글 개수 지정
        pageable = PageRequest.of(page,5);

        // 전체 문의글 리스트 불러오기
        return inquiryRepository.findAllDesc(pageable);
    }

    // 글 작성한 내용 DB 에 저장
    public String create(InquiryDto dto) {

        // Forum_member 타입으로 작성자 id를 저장하기 위해서 DB 에서 가져옴
        Member save_member = memberRepository.findById(dto.getWriterID()).orElse(null);
        if(save_member == null){
            return "로그인한 사용자 정보가 존재하지 않음";
        }
        
        Inquiry inquiry;
        String secret;

        //만약 비밀글 여부에 체크를 안한 경우
        if(dto.getSecret() == null){
            secret = "N";
        }
        else{
            // 체크한 경우("Y" 값이 들어있음)
            secret = dto.getSecret();
        }
        
        // dto -> entity 변환
        inquiry = Inquiry.builder()
                .member(save_member)
                .title(dto.getTitle())
                .content(dto.getContent().replaceAll("TempImage", "SavedImage"))
                .respond("N")
                .image(dto.getImage())
                .category(dto.getCategory())
                .secret(secret)
                .build();

        if(inquiry.getId() != null) {
            return "작성요청실패";
        }

        // 게시글에 이미지가 있는 경우에만 작동
        if((dto.getImage() != null) && (!dto.getImage().isEmpty())){
            // 파일 업로드 경로
            //final Path FILE_ROOT = Paths.get("./").toAbsolutePath().normalize();
            //final String TempPath = FILE_ROOT+ "/upload/inquiry/TempImage/";
            //final String SavedPath = FILE_ROOT+ "/upload/inquiry/SavedImage/";

            // 이미지 파일 이름 목록
            String[] fileName_arr = dto.getImage().split(",");

            // 이미지 파일 TempImage ->  SavedImage 이동
            for (String fileName : fileName_arr) {
                try {
                    //MOVE 전  대상 파일
                    //Path TempFile = Paths.get(TempPath + fileName);
                    //MOVE 후  대상 파일
                    //Path SavedFile = Paths.get(SavedPath + fileName);
                    //Files.move(TempFile, SavedFile, StandardCopyOption.REPLACE_EXISTING);

                    BlobId TempBlobId = BlobId.of(bucketName, "upload/inquiry/TempImage/" + fileName);
                    BlobId SavedBlobId = BlobId.of(bucketName, "upload/inquiry/SavedImage/" + fileName);

                    // 1. 파일 복사
                    Storage.CopyRequest copyRequest = Storage.CopyRequest.of(TempBlobId, SavedBlobId);
                    storage.copy(copyRequest);

                    // 2. 원본 삭제
                    boolean deleted = storage.delete(TempBlobId);

                    if (!deleted) {
                        throw new RuntimeException("파일 이동 중 삭제 실패");
                    }

                } catch (Exception e) {
                    return "imageMove Exception [Err_Msg]: " + e.getMessage();
                }
            }
        }

        // 게시글 등록
        inquiryRepository.save(inquiry);
        return "Success";
    }

    // 단순 게시글 조회
    public Inquiry show(Long id) {
        return inquiryRepository.findById(id).orElse(null);
    }

    // 조회수 증가 처리
    public String view_update(Long id) {
        Inquiry target = inquiryRepository.findById(id).orElse(null);

        if(target == null){
            return "해당 문의글이 존재하지 않습니다.";
        }
        else{
            // 조회수가 증가할 때마다 수정 시간이 바뀌면 안되기 때문에 JPQL 로 update
            Integer viewsAdd = target.getViews();
            inquiryRepository.updateViewsCount(++viewsAdd, target.getId());
            return "Success";
        }
    }

    // 작성자 ID와 로그인한 사용자 ID 비교하기
    public boolean SearchID(Long inquiryId, String LoginId) {

        String writerID = findWriterId(inquiryId);

        if(writerID == null) {
            return false;
        }

        // 글 작성자와 로그인한 사용자가 같으면 true
        if(writerID.equals(LoginId)) {
            return true;
        }
        else{return false;}
    }

    // 게시글 ID로 작성자 ID 찾기
    public String findWriterId(Long inquiryId) {
        // 게시글 아이디를 검색해서 게시글 정보 가져옴
        Inquiry target = inquiryRepository.findById(inquiryId).orElse(null);

        // 게시글이 존재하지 않거나 게시글 작성자가 탈퇴한 경우
        if(target == null || target.getMember() == null) {
            return null;
        }

        // entity -> dto 변환
        InquiryDto dto = InquiryDto.builder()
                .writerID(target.getMember().getId())
                .build();

        return dto.getWriterID();
    }

    // 게시글 수정을 위한 조회(content 내 img 태그의 src 경로 변경)
    public InquiryDto show_update(Long id) {
        Inquiry result = inquiryRepository.findById(id).orElse(null);

        // 조회할 게시글 또는 게시글 작성자 ID가 없으면 null 반환
        if((result == null) || (result.getMember() == null)){
            return null;
        }

        // 게시글에 첨부된 이미지 파일 목록 배열 생성 후 SavedImage ->  TempImage 복사
        if((result.getImage() != null) && (!result.getImage().isEmpty())){ // 게시글에 이미지가 있는 경우에만 작동
            // 파일 업로드 경로
            //final Path FILE_ROOT = Paths.get("./").toAbsolutePath().normalize();
            //final String TempPath = FILE_ROOT + "/upload/inquiry/TempImage/";
            //final String SavedPath = FILE_ROOT + "/upload/inquiry/SavedImage/";

            // 이미지 파일 이름 목록
            String[] fileName_arr = result.getImage().split(",");

            // 이미지 파일 SavedImage ->  TempImage 복사
            for (String fileName : fileName_arr) {
                try {
                    // 원본 파일
                    //Path SavedFile = Paths.get(SavedPath + fileName);
                    // 복사 파일
                    //Path TempFile = Paths.get(TempPath + fileName);
                    //Files.copy(SavedFile, TempFile, StandardCopyOption.REPLACE_EXISTING);

                    BlobId SavedBlobId = BlobId.of(bucketName, "upload/inquiry/SavedImage/" + fileName);
                    BlobId TempBlobId = BlobId.of(bucketName, "upload/inquiry/TempImage/" + fileName);


                    // 파일 복사
                    Storage.CopyRequest copyRequest = Storage.CopyRequest.of(SavedBlobId, TempBlobId);
                    storage.copy(copyRequest);

                } catch (Exception e) {
                    return null;
                }
            }
        }

        // entity -> dto 변환 후 리턴
        return InquiryDto.builder()
                .id(result.getId())
                .title(result.getTitle())
                .content(result.getContent().replaceAll("SavedImage", "TempImage"))
                .views(result.getViews())
                .createdDate(result.getCreatedDate())
                .modifiedDate(result.getModifiedDate())
                .category(result.getCategory())
                .secret(result.getSecret())
                .writerID(result.getMember().getId())
                .image(result.getImage())
                .build();
    }

    // 게시글 수정
    public Inquiry update(InquiryDto dto) {
        // 수정할 글 가져오기
        Inquiry target = inquiryRepository.findById(dto.getId()).orElse(null);

        if(target == null) {
            return null;
        }

        // 수정할 대상 객체
        Inquiry inquiryModify;

        // 파일 업로드 경로
        //final Path FILE_ROOT = Paths.get("./").toAbsolutePath().normalize();
        //final String SavedPath = FILE_ROOT + "/upload/inquiry/SavedImage/";
        //final String TempPath = FILE_ROOT + "/upload/inquiry/TempImage/";

        // 원본 이미지 목록
        String[] origin_fileName_arr = target.getImage().split(",");
        // 수정 이미지 목록
        String[] update_fileName_arr = dto.getImage().split(",");

        // 수정 전 이미지 존재 O, 수정 후 이미지 존재 O
        if(!target.getImage().isEmpty() && !dto.getImage().isEmpty()){
            // 원본 이미지 목록과 사용자가 수정한 이미지 목록과 비교하여 일치 여부 저장
            boolean isMatched = false;

            // 원본 이미지 목록과 사용자가 수정한 이미지 목록 비교
            for (String origin_fileName : origin_fileName_arr) {
                for (String update_fileName : update_fileName_arr) {
                    // 만약 일치한다면 isMatched 를 true 로 변경 후 break
                    if (origin_fileName.equals(update_fileName)) {
                        isMatched = true;
                        break;
                    }
                }
                // for 루프가 끝나면 매칭되지 못한 원본 이미지 목록 찾아서 삭제(사용자가 수정했기 때문에 원본 이미지 목록과 매칭되지 못한 것)
                if (!isMatched) {
//                    File file = new File(SavedPath + origin_fileName);
//                    if (file.exists()) {
//                        if (file.delete())
//                            log.info("파일 삭제 완료");
//                        else
//                            log.info("파일 삭제 실패");
//                    }
                    BlobId SavedBlobId = BlobId.of(bucketName, "upload/inquiry/SavedImage/" + origin_fileName);
                    boolean deleted = storage.delete(SavedBlobId);

                    if (!deleted) {
                        throw new RuntimeException("파일 삭제 실패");
                    }
                }
                // 다음 for 루프 검사를 위해 isMatched 변수 값 초기화
                isMatched = false;
            }

            // 이미지 목록 비교가 끝나면 파일 덮어쓰기 진행 TempImage -> SavedImage 이동
//            for (String fileName : update_fileName_arr) {
//                try {
//                    //MOVE 전  대상 파일
//                    Path TempFile = Paths.get(TempPath + fileName);
//                    //MOVE 후  대상 파일
//                    Path SavedFile = Paths.get(SavedPath + fileName);
//                    Files.move(TempFile, SavedFile, StandardCopyOption.REPLACE_EXISTING); // 파일 덮어쓰기
//                } catch (IOException e) {
//                    return null;
//                }
//            }

            // 수정한 이미지 목록과 원본 이미지 목록 비교
            for (String update_fileName : update_fileName_arr) {
                for (String origin_fileName : origin_fileName_arr) {
                    // 만약 일치한다면 isMatched 를 true 로 변경 후 break
                    if (update_fileName.equals(origin_fileName)) {
                        isMatched = true;
                        break;
                    }
                }
                // for 루프가 끝나고 매칭되지 못한 이미지는 곧 새롭게 추가된 이미지 이므로 Temp -> Saved 로 해당 파일 이동
                if (!isMatched) {
//                    File file = new File(SavedPath + origin_fileName);
//                    if (file.exists()) {
//                        if (file.delete())
//                            log.info("파일 삭제 완료");
//                        else
//                            log.info("파일 삭제 실패");
//                    }
                    BlobId TempBlobId = BlobId.of(bucketName, "upload/inquiry/TempImage/" + update_fileName);
                    BlobId SavedBlobId = BlobId.of(bucketName, "upload/inquiry/SavedImage/" + update_fileName);

                    // 1. 파일 복사
                    Storage.CopyRequest copyRequest = Storage.CopyRequest.of(TempBlobId, SavedBlobId);
                    storage.copy(copyRequest);

                    // 2. 원본 삭제
                    boolean deleted = storage.delete(TempBlobId);

                    if (!deleted) {
                        throw new RuntimeException("파일 이동 중 삭제 실패");
                    }
                }
                // 다음 for 루프 검사를 위해 isMatched 변수 값 초기화
                isMatched = false;
            }

            inquiryModify = target.toBuilder()
                    .title(dto.getTitle())
                    .content(dto.getContent().replaceAll("TempImage", "SavedImage"))
                    .image(dto.getImage())
                    .category(dto.getCategory())
                    .secret(dto.getSecret())
                    .build();
        }

        //수정 전 이미지 존재 O, 수정 후 이미지 존재 X
        else if(!target.getImage().isEmpty()) {
            for (String origin_fileName : origin_fileName_arr) {
//                File file = new File(SavedPath + origin_fileName);
//                if (file.exists()) {
//                    if (file.delete())
//                        log.info("파일 삭제 완료2");
//                    else
//                        log.info("파일 삭제 실패2");
//                }
                BlobId SavedBlobId = BlobId.of(bucketName, "upload/inquiry/SavedImage/" + origin_fileName);
                boolean deleted = storage.delete(SavedBlobId);

                if (!deleted) {
                    throw new RuntimeException("파일 삭제 실패");
                }
            }
            inquiryModify = target.toBuilder()
                    .title(dto.getTitle())
                    .content(dto.getContent())
                    .image(dto.getImage())
                    .category(dto.getCategory())
                    .secret(dto.getSecret())
                    .build();
        }

        // 수정 전 이미지 존재 X, 수정 후 이미지 존재 O
        else if(!dto.getImage().isEmpty()) {
            for (String fileName : update_fileName_arr) {
                try {
                    //MOVE 전  대상 파일
                    //Path TempFile = Paths.get(TempPath + fileName);
                    //MOVE 후  대상 파일
                    //Path SavedFile = Paths.get(SavedPath + fileName);
                    //Files.move(TempFile, SavedFile, StandardCopyOption.REPLACE_EXISTING);

                    BlobId TempBlobId = BlobId.of(bucketName, "upload/inquiry/TempImage/" + fileName);
                    BlobId SavedBlobId = BlobId.of(bucketName, "upload/inquiry/SavedImage/" + fileName);

                    // 1. 파일 복사
                    Storage.CopyRequest copyRequest = Storage.CopyRequest.of(TempBlobId, SavedBlobId);
                    storage.copy(copyRequest);

                    // 2. 원본 삭제
                    boolean deleted = storage.delete(TempBlobId);

                    if (!deleted) {
                        throw new RuntimeException("파일 이동 중 삭제 실패");
                    }

                } catch (Exception e) {
                    return null;
                }

            }
            inquiryModify = target.toBuilder()
                    .title(dto.getTitle())
                    .content(dto.getContent().replaceAll("TempImage", "SavedImage"))
                    .image(dto.getImage())
                    .category(dto.getCategory())
                    .secret(dto.getSecret())
                    .build();
        }

        // 수정 전, 수정 후 이미지 존재 X
        else{
            inquiryModify = target.toBuilder()
                    .title(dto.getTitle())
                    .content(dto.getContent())
                    .image(dto.getImage())
                    .category(dto.getCategory())
                    .secret(dto.getSecret())
                    .build();
        }
        return inquiryRepository.save(inquiryModify);
    }

    // 문의글 답변 여부 가져오기
    public String getRespond(Long id) {
        return inquiryRepository.findRespondById(id);
    }

    // 게시글 삭제
    public String delete(Long id) {
        Inquiry target = inquiryRepository.findById(id).orElse(null);

        if(target == null){
            return "삭제요청실패";
        }

        // entity -> dto 변환
        InquiryDto dto = InquiryDto.builder()
                .writerID(target.getMember().getId())
                .image(target.getImage())
                .build();

        // 게시글에 이미지가 있는 경우에만 작동
        if(!dto.getImage().isEmpty()){
            // 파일 업로드 경로
            //final Path FILE_ROOT = Paths.get("./").toAbsolutePath().normalize();
            //final String uploadPath = FILE_ROOT + "/upload/inquiry/SavedImage/";

            // 게시글에 첨부된 이미지 삭제
            String[] fileName_arr = dto.getImage().split(",");
            for (String fileName : fileName_arr) {
                try {
                    //Path path = Paths.get(uploadPath, fileName);
                    //Files.delete(path);

                    boolean deleted = storage.delete(bucketName, "upload/inquiry/SavedImage/" + fileName);
                    if (!deleted) {
                        throw new RuntimeException("GCS 파일 삭제 실패: 파일이 존재하지 않거나 권한 없음");
                    }
                } catch (Exception e) {
                    return "이미지 삭제 오류";
                }
            }
        }

        // 게시글 삭제
        inquiryRepository.delete(target);
        return "Success";
    }
    
    // 답변 상태를 'N' 으로 변경함
    public String change_respond_stat(Long inquiryId) {
        int result = inquiryRepository.updateRespondCk("N", inquiryId);
        if(result == 1){
            return "Success";
        }
        else{
            return "Fail";
        }
    }
}
