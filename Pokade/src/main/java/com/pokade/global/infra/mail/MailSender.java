package com.pokade.global.infra.mail;

public interface MailSender {
    void send(String to, String subject, String body);
}
