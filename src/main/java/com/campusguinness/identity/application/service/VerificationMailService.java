package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.port.MailDeliveryPort;
import com.campusguinness.identity.application.port.MailMessage;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class VerificationMailService {

    private final MailDeliveryPort mailDeliveryPort;
    private final EmailNormalizer emailNormalizer;
    private final AppMailProperties mailProperties;

    public VerificationMailService(MailDeliveryPort mailDeliveryPort,
            EmailNormalizer emailNormalizer,
            AppMailProperties mailProperties) {
        this.mailDeliveryPort = mailDeliveryPort;
        this.emailNormalizer = emailNormalizer;
        this.mailProperties = mailProperties;
    }

    public void sendVerificationMail(String email, String rawToken) {
        String recipient = emailNormalizer.normalize(email);
        String url = mailProperties.publicFrontendUrl()
                + "/verify-email?token=" + encode(rawToken);
        mailDeliveryPort.send(new MailMessage(
                recipient,
                "Verify your Campus Guinness email",
                "Please verify your email address by opening this link:\n\n" + url));
    }

    private String encode(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken required");
        }
        return URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
