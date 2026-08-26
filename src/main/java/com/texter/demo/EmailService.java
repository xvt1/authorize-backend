package com.texter.demo;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.resend.core.exception.ResendException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final Resend resend;

    @Value("${resend.from.email:onboarding@resend.dev}")
    private String fromEmail;

    public EmailService(@Value("${resend.api.key}") String apiKey) {
        this.resend = new Resend(apiKey);
    }

    public void sendVerificationCode(String to, String code) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(to)
                .subject("Код підтвердження")
                .html(buildVerificationHtml(code))
                .build();

        try {
            CreateEmailResponse response = resend.emails().send(params);
            // за бажанням: log.info("Email sent, id={}", response.getId());
        } catch (ResendException e) {
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