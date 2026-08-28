package com.texter.demo;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final Resend resend;

    @Value("${resend.from-email}")
    private String fromEmail;

    public EmailService(Resend resend) {
        this.resend = resend;
    }

    public void sendVerificationCode(String to, String code) {
        String html = """
                <div style="font-family: sans-serif;">
                    <h2>Код подтверждения</h2>
                    <p>Ваш код подтверждения:</p>
                    <h1 style="letter-spacing: 4px;">%s</h1>
                    <p>Если вы не запрашивали код — просто проигнорируйте это письмо.</p>
                </div>
                """.formatted(code);

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(to)
                .subject("Ваш код подтверждения")
                .html(html)
                .build();

        try {
            CreateEmailResponse response = resend.emails().send(params);
            // response.getId() — можно залогировать id письма для отладки
        } catch (Exception e) {
            throw new RuntimeException("Не удалось отправить письмо через Resend", e);
        }
    }
}