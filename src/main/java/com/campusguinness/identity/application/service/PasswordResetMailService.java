package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.port.MailDeliveryPort;
import com.campusguinness.identity.application.port.MailMessage;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class PasswordResetMailService {

    private final MailDeliveryPort mailDeliveryPort;
    private final EmailNormalizer emailNormalizer;
    private final AppMailProperties mailProperties;

    public PasswordResetMailService(MailDeliveryPort mailDeliveryPort,
            EmailNormalizer emailNormalizer,
            AppMailProperties mailProperties) {
        this.mailDeliveryPort = mailDeliveryPort;
        this.emailNormalizer = emailNormalizer;
        this.mailProperties = mailProperties;
    }

    public void sendPasswordResetMail(String email, String rawToken) {
        String recipient = emailNormalizer.normalize(email);
        String url = mailProperties.publicFrontendUrl()
                + "/reset-password?token=" + encode(rawToken);
        mailDeliveryPort.send(new MailMessage(
                recipient,
                "Reset your Campus Guinness password",
                "Please reset your password by opening this link:\n\n" + url));
    }

    private String encode(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken required");
        }
        return URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
