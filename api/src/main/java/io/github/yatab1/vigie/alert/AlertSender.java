package io.github.yatab1.vigie.alert;

/** Delivers a plain-text alert to a user. */
public interface AlertSender {

    void send(String to, String subject, String body);
}
