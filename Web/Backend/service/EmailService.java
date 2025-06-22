package com.example.capstone.service;

import com.example.capstone.entity.Member;
import com.example.capstone.repository.MemberRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@RequiredArgsConstructor // 생성자 주입 사용하기
@Service
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;

    private final MemberRepository memberRepository;

    // 인증번호 전송용
    public String sendVerificationCode(String recipient) throws MessagingException {
        String verificationCode = generateEmailCode();
        String subject = "EmoSync 인증번호";

        String message = "<html>" +
                "<body style='font-family: Arial, sans-serif; background-color: #f4f7fc; color: #333; padding: 20px;'>" +

                "<div style='text-align: center; margin-bottom: 20px;'>" +
                "<h2 style='font-size: 28px; color: #007bff;'>EmoSync</h2>" +
                "</div>" +

                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 20px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);'>" +

                "<h1 style='font-size: 24px; color: #007bff; text-align: center;'>인증번호</h1>" + "<br>" +

                "<p style='font-size: 16px; line-height: 1.5;'>안녕하세요, <strong>고객님</strong></p>" +

                "<p style='font-size: 16px; line-height: 1.5;'>요청하신 인증번호 입니다. 아래에서 확인해주세요.</p>" + "<br>" +

                "<div style='background-color: #f9f9f9; padding: 15px; border-left: 4px solid #007bff; margin-bottom: 20px;'>"+
                "<strong>인증번호:</strong> <br>" +
                "<p>"+verificationCode+"</p>" +
                "</div>" + "<br>" +

                "<div style='text-align: center; margin-top: 30px;'>" +
                "</div>" +

                "</div>" +

                "<div style='text-align: center; font-size: 12px; color: #888; margin-top: 30px;'>" +
                "<p>본 메일은 발신 전용 메일입니다. 회신하지 마십시오.</p>" +
                "</div>" +

                "</body>" +
                "</html>";

        sendEmail(recipient, subject, message);
        return verificationCode; // 세션에 저장하기위해 반환
    }

    public void sendEmail(String recipient, String subject, String text) throws MessagingException {

        // MimeMessage 객체 생성
        MimeMessage message = javaMailSender.createMimeMessage();

        // MimeMessageHelper 객체 생성 (HTML 이메일 지원을 위해 true 설정)
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

        helper.setTo(recipient); // 수신자 설정
        helper.setSubject(subject); // 제목 설정
        helper.setText(text, true); // HTML 본문 설정 (두 번째 인자를 true 로 설정하여 HTML 형식으로 인식)
        javaMailSender.send(message); // 메일 전송
    }

    /**
     * 이메일 인증번호를 생성하는 메서드
     *
     * @return 6자리 랜덤 숫자 인증번호
     */
    public String generateEmailCode() {
        int codeLength = 6;  // 코드자리 6자리로 설정
        String code_num = "0123456789";
        StringBuilder sb = new StringBuilder(codeLength);
        Random random = new SecureRandom();

        for (int i = 0; i < codeLength; i++) {
            int index = random.nextInt(code_num.length());
            char randomChar = code_num.charAt(index);
            sb.append(randomChar);
        }
        return sb.toString();
    }

    // 이메일로 아이디 전송(아이디 찾기)
    public void sendUserId(String recipient) throws MessagingException {
        String userId = memberRepository.findIdByEmail(recipient);
        String subject = "[EmoSync] 아이디 찾기";

        String message = "<html>" +
                "<body style='font-family: Arial, sans-serif; background-color: #f4f7fc; color: #333; padding: 20px;'>" +

                "<div style='text-align: center; margin-bottom: 20px;'>" +
                "<h2 style='font-size: 28px; color: #007bff;'>EmoSync</h2>" +
                "</div>" +

                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 20px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);'>" +

                "<h1 style='font-size: 24px; color: #007bff; text-align: center;'>아이디 찾기</h1>" + "<br>" +

                "<p style='font-size: 16px; line-height: 1.5;'>안녕하세요, <strong>고객님</strong></p>" +

                "<p style='font-size: 16px; line-height: 1.5;'>요청하신 아이디 입니다. 아래에서 확인해주세요.</p>" + "<br>" +

                "<div style='background-color: #f9f9f9; padding: 15px; border-left: 4px solid #007bff; margin-bottom: 20px;'>"+
                "<strong>아이디:</strong> <br>" +
                "<p>"+userId+"</p>" +
                "</div>" + "<br>" +

                "<p style='text-align: center;'>자신이 한 요청이 아니라면 계정 보안을 강화해주세요.</p>" +

                "<div style='text-align: center; margin-top: 30px;'>" +
                "</div>" +

                "</div>" +

                "<div style='text-align: center; font-size: 12px; color: #888; margin-top: 30px;'>" +
                "<p>본 메일은 발신 전용 메일입니다. 회신하지 마십시오.</p>" +
                "</div>" +

                "</body>" +
                "</html>";

        sendEmail(recipient, subject, message);
    }

    // 이메일로 임시 비밀번호 전송(비밀번호 찾기)
    public void sendTempPassword(String recipient, String userId) throws MessagingException {

        // 비밀번호 update 로직
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        // 임시 비밀번호를 발급하여 암호화
        String originalPassword = getRandomPassword(9);
        String securePassword = encoder.encode(originalPassword);

        // 비밀번호 정보를 변경할 회원 정보 가져오기
        Member member = memberRepository.findById(userId).orElse(null);

        // 회원 정보를 정상적으로 찾은 경우
        if(member != null) {
            // 임시 비밀번호로 변경
            Member memberPwModify = member.toBuilder()
                    .password(securePassword)
                    .build();
            memberRepository.save(memberPwModify);
        }

        String subject = "[EmoSync] 비밀번호 찾기";
        String message = "<html>" +
                "<body style='font-family: Arial, sans-serif; background-color: #f4f7fc; color: #333; padding: 20px;'>" +

                "<div style='text-align: center; margin-bottom: 20px;'>" +
                "<h2 style='font-size: 28px; color: #007bff;'>EmoSync</h2>" +
                "</div>" +

                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 20px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);'>" +

                "<h1 style='font-size: 24px; color: #007bff; text-align: center;'>비밀번호 찾기</h1>" + "<br>" +

                "<p style='font-size: 16px; line-height: 1.5;'>안녕하세요, <strong>고객님</strong></p>" +

                "<p style='font-size: 16px; line-height: 1.5;'>임시 비밀번호가 설정되었습니다. 아래에서 확인해주세요.</p>" + "<br>" +

                "<div style='background-color: #f9f9f9; padding: 15px; border-left: 4px solid #007bff; margin-bottom: 20px;'>"+
                "<strong>임시 비밀번호:</strong> <br>" +
                "<p>"+originalPassword+"</p>" +
                "</div>" + "<br>" +

                "<p style='font-size: 16px;'>임시 비밀번호는 로그인 후에 꼭 변경해주세요.</p>" +

                "<p style='text-align: center;'>자신이 한 요청이 아니라면 계정 보안을 강화해주세요.</p>" +

                "<div style='text-align: center; margin-top: 30px;'>" +
                "</div>" +

                "</div>" +

                "<div style='text-align: center; font-size: 12px; color: #888; margin-top: 30px;'>" +
                "<p>본 메일은 발신 전용 메일입니다. 회신하지 마십시오.</p>" +
                "</div>" +

                "</body>" +
                "</html>";

        sendEmail(recipient, subject, message);
    }

    // 문의글에 답변 등록되면 이메일로 알림 보내기
    public void sendRespondNotification(String userId, String respondMsg) throws MessagingException {
        Member member = memberRepository.findById(userId).orElse(null);
        if(member != null) {
            String recipient = member.getEmail();
            String subject = "[EmoSync] 문의글 답변 등록";
            String message = "<html>" +
                    "<body style='font-family: Arial, sans-serif; background-color: #f4f7fc; color: #333; padding: 20px;'>" +

                    "<div style='text-align: center; margin-bottom: 20px;'>" +
                    "<h2 style='font-size: 28px; color: #007bff;'>EmoSync</h2>" +
                    "</div>" +

                    "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 20px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);'>" +

                    "<h1 style='font-size: 24px; color: #007bff; text-align: center;'>문의에 답변이 등록되었습니다.</h1>" + "<br>" +

                    "<p style='font-size: 16px; line-height: 1.5;'>안녕하세요, <strong>고객님</strong></p>" +

                    "<p style='font-size: 16px; line-height: 1.5;'>작성하신 문의글에 대한 답변이 등록되었습니다. 아래에서 답변을 확인해 주세요:</p>" + "<br>" +

                    "<div style='background-color: #f9f9f9; padding: 15px; border-left: 4px solid #007bff; margin-bottom: 20px;'>"+
                    "<strong>답변 내용:</strong> <br>" +
                    "<p>"+respondMsg+"</p>" +
                    "</div>" + "<br>" +

                    "<p style='font-size: 16px;'>더 궁금한 점이 있으시면 언제든지 문의해 주세요.</p>" +

                    "<p style='text-align: center;'>감사합니다.</p>" +

                    "<div style='text-align: center; margin-top: 30px;'>" +
                    "</div>" +

                    "</div>" +

                    "<div style='text-align: center; font-size: 12px; color: #888; margin-top: 30px;'>" +
                    "<p>본 메일은 발신 전용 메일입니다. 회신하지 마십시오.</p>" +
                    "</div>" +

                    "</body>" +
                    "</html>";


            sendEmail(recipient, subject, message);
        }
    }


    // 플랜 만료일이 7일 남은 유저에게 이메일 보내기
    public void sendExpiryNotifications(String userId, String email, LocalDateTime subscription_end_date) throws MessagingException {
        // 유효기간 날짜 형식 변환
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
        String formattedDate = subscription_end_date.format(formatter);
        
        String subject = "[EmoSync] Premium 요금제 만료일 안내";
        String message = "<html>" +
                "<body style='font-family: Arial, sans-serif; background-color: #f4f7fc; color: #333; padding: 20px;'>" +

                "<div style='text-align: center; margin-bottom: 20px;'>" +
                "<h2 style='font-size: 28px; color: #007bff;'>EmoSync</h2>" +
                "</div>" +

                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 20px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);'>" +

                "<h1 style='font-size: 24px; color: #007bff; text-align: center;'> Premium 요금제 만료일 안내드립니다.</h1>" + "<br>" +

                "<p style='font-size: 16px; line-height: 1.5;'>안녕하세요, <strong>"+ userId +"고객님</strong></p>" +

                "<p style='font-size: 16px; line-height: 1.5;'>가입하신 Premium 요금제가 7일 후 만료됩니다. 서비스를 끊김 없이 이용하시려면 미리 유효기한을 연장해 주세요.</p>" + "<br>" +

                "<div style='background-color: #f9f9f9; padding: 15px; border-left: 4px solid #007bff; margin-bottom: 20px;'>"+
                "<strong>유효기한:</strong> <br>" +
                "<p>"+formattedDate+"</p>" +
                "</div>" + "<br>" +

                "<p style='font-size: 16px;'>궁금한 점이 있으시면 언제든지 문의해 주세요.</p>" +

                "<p style='text-align: center;'>감사합니다.</p>" +

                "<div style='text-align: center; margin-top: 30px;'>" +
                "</div>" +

                "</div>" +

                "<div style='text-align: center; font-size: 12px; color: #888; margin-top: 30px;'>" +
                "<p>본 메일은 발신 전용 메일입니다. 회신하지 마십시오.</p>" +
                "</div>" +

                "</body>" +
                "</html>";


        sendEmail(email, subject, message);
    }
    

    // 임시 비밀번호 생성
    public String getRandomPassword(int length){
        char[] rndAllCharacters = new char[]{
                //number
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                //uppercase
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
                'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
                //lowercase
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
                'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
                //special symbols
                '@', '$', '!', '%', '*', '?', '&'
        };

        char[] numberCharacters = new char[] {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
        };

        char[] uppercaseCharacters = new char[] {
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
                'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
        };

        char[] lowercaseCharacters = new char[] {
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
                'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
        };

        char[] specialSymbolCharacters = new char[] {
                '@', '$', '!', '%', '*', '?', '&'
        };


        SecureRandom random = new SecureRandom();
        StringBuilder stringBuilder = new StringBuilder();

        List<Character> passwordCharacters = new ArrayList<>();

        int numberCharactersLength = numberCharacters.length;
        passwordCharacters.add(numberCharacters[random.nextInt(numberCharactersLength)]);

        int uppercaseCharactersLength = uppercaseCharacters.length;
        passwordCharacters.add(uppercaseCharacters[random.nextInt(uppercaseCharactersLength)]);

        int lowercaseCharactersLength = lowercaseCharacters.length;
        passwordCharacters.add(lowercaseCharacters[random.nextInt(lowercaseCharactersLength)]);

        int specialSymbolCharactersLength = specialSymbolCharacters.length;
        passwordCharacters.add(specialSymbolCharacters[random.nextInt(specialSymbolCharactersLength)]);

        int rndAllCharactersLength = rndAllCharacters.length;
        for (int i = 0; i < length-4; i++) {
            passwordCharacters.add(rndAllCharacters[random.nextInt(rndAllCharactersLength)]);
        }

        Collections.shuffle(passwordCharacters);

        for (Character character : passwordCharacters) {
            stringBuilder.append(character);
        }

        return stringBuilder.toString();
    }
}
