package com.campusguinness.identity.application.port;

public interface MailDeliveryPort {
    void send(MailMessage message);
}
