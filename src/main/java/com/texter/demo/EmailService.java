package com.texter.demo;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationCode(String to, String code) {
        String html = """
                <div style="font-family: sans-serif;">
                    <h2>Код підтвердження</h2>
                    <p>Ваш код підтвердження:</p>
                    <h1 style="letter-spacing: 4px;">%s</h1>
                    <p>Якщо ви не запитували код — просто проігноруйте цей лист.</p>
                </div>
                """.formatted(code);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Ваш код підтвердження");
            helper.setText(html, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Не вдалося надіслати лист через Gmail SMTP", e);
        }
    }
}