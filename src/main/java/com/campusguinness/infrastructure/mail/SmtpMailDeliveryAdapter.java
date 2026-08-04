package com.campusguinness.infrastructure.mail;

import com.campusguinness.identity.application.port.MailDeliveryPort;
import com.campusguinness.identity.application.port.MailMessage;
import com.campusguinness.identity.application.service.AppMailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailDeliveryAdapter implements MailDeliveryPort {

    private final JavaMailSender sender;
    private final AppMailProperties properties;

    public SmtpMailDeliveryAdapter(JavaMailSender sender, AppMailProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override
    public void send(MailMessage message) {
        var mail = new SimpleMailMessage();
        mail.setFrom(properties.from());
        mail.setTo(message.recipient());
        mail.setSubject(message.subject());
        mail.setText(message.textBody());
        sender.send(mail);
    }
}
