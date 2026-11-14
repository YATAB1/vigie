package io.github.yatab1.vigie.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StaleScanSweeper {

    private static final Logger log = LoggerFactory.getLogger(StaleScanSweeper.class);

    private final ScanService scans;

    public StaleScanSweeper(ScanService scans) {
        this.scans = scans;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void sweep() {
        int failed = scans.failStaleScans();
        if (failed > 0) {
            log.warn("{} scan(s) timed out without a result", failed);
        }
    }
}
