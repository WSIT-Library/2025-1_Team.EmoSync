package com.example.capstone.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class EncryptionUtil {

    // AES 암호화
    public static String encrypt(String data, String key) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    // AES 복호화
    public static String decrypt(String encryptedData, String key) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes);
    }

    // 암호화된 데이터를 새로운 파일에 저장 (파일 이름에 'encrypted' 추가)
    public static void encryptFile(String inputFilePath, String key) throws Exception {
        byte[] fileBytes = Files.readAllBytes(Paths.get(inputFilePath));
        String encryptedData = encrypt(new String(fileBytes), key);

        // 기존 파일 이름에서 'encrypted' 접미사 추가
        String outputFileName = inputFilePath.replace(".json", "_encrypted.json");

        // 암호화된 데이터를 새로운 파일에 저장
        Files.write(Paths.get(outputFileName), encryptedData.getBytes());
    }

    // GCP 키 파일을 복호화하여 저장
    public static void decryptFile(String filePath, String outputFilePath, String key) throws Exception {
        byte[] fileBytes = Files.readAllBytes(new File(filePath).toPath());
        String encryptedData = new String(fileBytes);
        String decryptedData = decrypt(encryptedData, key);

        // 기존 파일 이름에서 'encrypted' 접미사 제거
        String outputFileName = outputFilePath.replace("_encrypted.json", ".json");
        
        Files.write(Paths.get(outputFileName), decryptedData.getBytes());
    }
}
