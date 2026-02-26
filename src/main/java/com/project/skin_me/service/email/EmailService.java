package com.project.skin_me.service.email;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url:https://skinme.store}")
    private String frontendUrl;

    @Value("${spring.mail.username:noreply@skinme.store}")
    private String fromEmail;

    /**
     * Send password reset email with token
     */
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Password Reset Request - SkinMe");
            
            String resetUrl = frontendUrl + "/reset-password?token=" + resetToken + "&email=" + toEmail;
            String emailBody = buildPasswordResetEmailBody(resetUrl, resetToken);
            
            message.setText(emailBody);
            mailSender.send(message);
            
            logger.info("Password reset email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to send password reset email to: {}. Error: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send password reset email: " + e.getMessage(), e);
        }
    }

    /**
     * Build password reset email body
     */
    private String buildPasswordResetEmailBody(String resetUrl, String resetToken) {
        return String.format(
            "Dear User,\n\n" +
            "You have requested to reset your password for your SkinMe account.\n\n" +
            "Please click on the following link to reset your password:\n" +
            "%s\n\n" +
            "Or use this token manually:\n" +
            "Token: %s\n\n" +
            "This link will expire in 1 hour.\n\n" +
            "If you did not request this password reset, please ignore this email.\n\n" +
            "Best regards,\n" +
            "SkinMe Team",
            resetUrl, resetToken
        );
    }
}
