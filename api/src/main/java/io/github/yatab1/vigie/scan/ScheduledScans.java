package io.github.yatab1.vigie.scan;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Rescans every verified domain once a day, so the score history fills itself. */
@Component
public class ScheduledScans {

    private static final Logger log = LoggerFactory.getLogger(ScheduledScans.class);

    private final ScanService scans;
    private final boolean enabled;

    public ScheduledScans(ScanService scans, @Value("${vigie.schedule.enabled:true}") boolean enabled) {
        this.scans = scans;
        this.enabled = enabled;
    }

    /** Runs hourly; each domain is rescanned when its last scan is more than a day old. */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    public void run() {
        if (!enabled) {
            return;
        }
        int queued = scans.scanStaleDomains(Duration.ofHours(24));
        if (queued > 0) {
            log.info("Scheduled {} automatic scan(s)", queued);
        }
    }
}
