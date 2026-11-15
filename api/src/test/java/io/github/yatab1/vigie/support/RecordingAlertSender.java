package io.github.yatab1.vigie.support;

import io.github.yatab1.vigie.alert.AlertSender;
import java.util.ArrayList;
import java.util.List;

/** Test double: keeps alerts in memory instead of sending emails. */
public class RecordingAlertSender implements AlertSender {

    public record Alert(String to, String subject, String body) {
    }

    private final List<Alert> alerts = new ArrayList<>();

    @Override
    public synchronized void send(String to, String subject, String body) {
        alerts.add(new Alert(to, subject, body));
    }

    public synchronized List<Alert> alerts() {
        return List.copyOf(alerts);
    }

    public synchronized void clear() {
        alerts.clear();
    }
}
