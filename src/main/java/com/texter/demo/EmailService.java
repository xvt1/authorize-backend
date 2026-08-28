package com.texter.demo;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationCode(String to, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setSubject("Код підтвердження");
            helper.setText(buildVerificationHtml(code), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Не вдалося надіслати лист із кодом підтвердження", e);
        }
    }

    private String buildVerificationHtml(String code) {
        return """
                <div style="font-family: sans-serif; max-width: 480px; margin: 0 auto;">
                    <h2>Підтвердження реєстрації</h2>
                    <p>Ваш код підтвердження:</p>
                    <p style="font-size: 28px; font-weight: bold; letter-spacing: 4px;">%s</p>
                    <p>Код дійсний протягом 5 хвилин. Якщо ви не реєструвалися — проігноруйте цей лист.</p>
                </div>
                """.formatted(code);
    }
}