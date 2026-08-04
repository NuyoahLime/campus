package com.campusguinness.identity.application.port;

public record MailMessage(
        String recipient,
        String subject,
        String textBody
) {
    public MailMessage {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject required");
        }
        if (textBody == null || textBody.isBlank()) {
            throw new IllegalArgumentException("textBody required");
        }
    }
}
