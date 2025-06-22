package com.example.capstone.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Slf4j
public class GCPKeyFileInitializer {
    @Value("${encryption.key}")  // 암호화 키를 환경 변수 또는 프로퍼티 파일로 설정
    private String encryptionKey;

    @Value("${gcp.key.encrypted.path}")  // 암호화된 key.json 파일 경로
    private String encryptedKeyFilePath;

    @Value("${gcp.key.decrypted.path}")  // 복호화된 key.json 파일 경로
    private String decryptedKeyFilePath;

    @PostConstruct
    public void init(){
        try{
            // 암호화된 파일 경로
            Path encrypted_path = Paths.get(encryptedKeyFilePath); 
            log.info("암호화 파일 경로 받아옴");

            // 암호화된 파일이 존재하는지 확인
            if (Files.exists(encrypted_path) && Files.isRegularFile(encrypted_path)) {
                log.info("암호화된 파일이 존재하는지 확인을 위한 if 문 진입");
                // 복호화된 파일 경로
                Path decrypted_path = Paths.get(decryptedKeyFilePath);
                log.info("복호화 파일 경로 받아옴");
                
                // 이미 복호화된 파일 존재하는지 검사
                if(Files.exists(decrypted_path) && Files.isRegularFile(decrypted_path)){
                    log.info("복호화된 파일이 존재");
                }
                
                // 복호화된 파일이 없기 때문에 복호화 진행
                else{
                    // 암호화된 GCP key 파일 복호화
                    EncryptionUtil.decryptFile(encryptedKeyFilePath, decryptedKeyFilePath, encryptionKey);
                    log.info("키 파일 복호화 진행");
                }
            } else {
                log.info("암호화된 파일 존재하지 않음");
                throw new Exception("암호화된 파일을 찾지 못해 예외 발생함");
                // 암호화된 파일이 없는 경우 암호화 진행
                //EncryptionUtil.encryptFile(decryptedKeyFilePath, encryptionKey);
                //log.info("키 파일 암호화 진행");
            }
        }
        catch(Exception e){
            log.info("에러 메세지: {}", e.getMessage());
        }
    }
}
