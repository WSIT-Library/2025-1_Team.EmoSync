package com.example.capstone.controller;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/upload/*")
@Slf4j
@RequiredArgsConstructor
public class ImageController {
    // 버킷 이름
    @Value("${spring.cloud.gcp.storage.bucket}")
    private String bucketName;

    private final Storage storage;
    
    // 파일 루트 경로(공통)
    //final Path FILE_ROOT = Paths.get("./").toAbsolutePath().normalize();
    
    // 문의글에 첨부된 이미지 파일 업로드 경로
    //private final String InquiryUploadPath = FILE_ROOT + "/upload/inquiry/TempImage/";

    @PostMapping("/inquiry/imageUpload")
    public ResponseEntity<?> imageUpload(@RequestParam MultipartFile file) {

        System.err.println("이미지 업로드");

        try {
            // 업로드 파일의 이름
            String originalFileName = file.getOriginalFilename();

            // 업로드 파일의 확장자
            assert originalFileName != null;
            String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));

            // 업로드 된 파일이 중복될 수 있어서 파일 이름 재설정
            String reFileName = UUID.randomUUID() + fileExtension;

            // 업로드 경로에 파일명을 변경하여 저장
            //file.transferTo(new File(InquiryUploadPath, reFileName));


            // Cloud 에 이미지 업로드
            BlobInfo blobInfo = storage.create(
                    BlobInfo.newBuilder(bucketName, "upload/inquiry/TempImage/" + reFileName)
                            .setContentType(fileExtension)
                            .build(),
                    file.getBytes()
            );

            // 파일이름을 재전송
            return ResponseEntity.ok(reFileName);
        }catch (Exception e) {
            log.error("imageUpload Exception [Err_Msg]: {}", e.getMessage());
            return ResponseEntity.badRequest().body("업로드 에러");
        }
    }


    @PostMapping("/inquiry/imageDelete")
    public ResponseEntity<?> imageDelete(@RequestParam String fileName) {
        System.err.println("이미지 삭제");

        try {
            //Path path = Paths.get(InquiryUploadPath, fileName);
            //Files.delete(path);
            boolean deleted = storage.delete(bucketName, "upload/inquiry/TempImage/" + fileName);
            if (!deleted) {
                throw new RuntimeException("GCS 파일 삭제 실패: 파일이 존재하지 않거나 권한 없음");
            }
            return ResponseEntity.ok(fileName);
        }catch (Exception e) {
            log.error("imageDelete Exception [Err_Msg]: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
