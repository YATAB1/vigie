package io.github.yatab1.vigie.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends alerts by email. Spring Boot only creates a JavaMailSender when
 * SPRING_MAIL_HOST is set; without it, alerts are written to the log instead.
 */
@Component
public class MailAlertSender implements AlertSender {

    private static final Logger log = LoggerFactory.getLogger(MailAlertSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AlertProperties properties;

    public MailAlertSender(ObjectProvider<JavaMailSender> mailSender, AlertProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("Mail is not configured; alert for a user not sent: {}", subject);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
        } catch (MailException exception) {
            // An unreachable mail server must not break scan processing.
            log.warn("Alert email could not be sent: {}", exception.getMessage());
        }
    }
}
