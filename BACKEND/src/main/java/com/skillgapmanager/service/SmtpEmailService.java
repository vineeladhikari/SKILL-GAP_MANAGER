package com.skillgapmanager.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService {
    private final JavaMailSender mailSender;
    private final String host;
    private final String username;
    private final String password;
    private final String from;

    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${spring.mail.host:}") String host,
                            @Value("${app.mail.username:}") String username,
                            @Value("${spring.mail.password:}") String password,
                            @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.host = host;
        this.username = username;
        this.password = password;
        this.from = from;
    }

    public boolean isConfigured() {
        return !host.isBlank() && !username.isBlank() && !password.isBlank() && !from.isBlank();
    }

    public void sendPasswordReset(String email, String otpCode) {
        if (!isConfigured()) {
            throw new IllegalStateException("SMTP email is not configured");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("Your Skill Gap Manager password reset code");
            helper.setText(
                    "<p>Your password reset code is:</p>"
                            + "<p><strong>" + otpCode + "</strong></p>"
                            + "<p>Enter this code in the app to reset your password. It expires in 20 minutes.</p>",
                    true);
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("SMTP email could not be delivered", exception);
        }
    }
}
