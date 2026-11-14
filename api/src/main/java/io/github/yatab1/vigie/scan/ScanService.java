package io.github.yatab1.vigie.scan;

import io.github.yatab1.vigie.common.ApiException;
import io.github.yatab1.vigie.domain.Domain;
import io.github.yatab1.vigie.domain.DomainService;
import io.github.yatab1.vigie.queue.JobQueue;
import io.github.yatab1.vigie.queue.QueueProperties;
import io.github.yatab1.vigie.queue.WorkerResult;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);
    private static final int MAX_CHECKS_JSON_LENGTH = 200_000;

    private final ScanRepository scans;
    private final DomainService domains;
    private final JobQueue jobQueue;
    private final QueueProperties queueProperties;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ScanService(
            ScanRepository scans,
            DomainService domains,
            JobQueue jobQueue,
            QueueProperties queueProperties,
            ObjectMapper mapper,
            Clock clock) {
        this.scans = scans;
        this.domains = domains;
        this.jobQueue = jobQueue;
        this.queueProperties = queueProperties;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Queues a scan. Only a domain whose ownership was proven can be scanned. */
    public Scan request(UUID userId, UUID domainId) {
        Domain domain = domains.get(userId, domainId);
        if (!domain.isVerified()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Verify ownership of this domain before scanning it");
        }
        if (scans.existsByDomainIdAndStatus(domainId, ScanStatus.QUEUED)) {
            throw new ApiException(HttpStatus.CONFLICT, "A scan is already running for this domain");
        }
        // Saved (and committed) before the job is sent, so the result always finds its scan.
        Scan scan = scans.save(new Scan(domainId, clock.instant()));
        try {
            jobQueue.enqueue(scan.getId(), "scan", Map.of("hostname", domain.getHostname()));
        } catch (RuntimeException exception) {
            log.error("Cannot enqueue scan job", exception);
            scan.fail("Scanning is temporarily unavailable", clock.instant());
            scans.save(scan);
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Scanning is temporarily unavailable");
        }
        return scan;
    }

    public List<Scan> list(UUID userId, UUID domainId) {
        domains.get(userId, domainId);
        return scans.findTop20ByDomainIdOrderByCreatedAtDesc(domainId);
    }

    public Scan get(UUID userId, UUID scanId) {
        Scan scan = scans.findById(scanId).orElseThrow(ScanService::notFound);
        try {
            domains.get(userId, scan.getDomainId());
        } catch (ApiException exception) {
            // Someone else's scan looks exactly like a scan that does not exist.
            throw notFound();
        }
        return scan;
    }

    public JsonNode checksOf(Scan scan) {
        if (scan.getChecks() == null) {
            return null;
        }
        return mapper.readTree(scan.getChecks());
    }

    @Transactional
    public void complete(UUID jobId, WorkerResult result) {
        Scan scan = scans.findById(jobId).orElse(null);
        if (scan == null || !scan.isQueued()) {
            // Unknown job, or a result that was already applied: ignore.
            return;
        }
        Instant now = clock.instant();
        if (!result.succeeded()) {
            scan.fail(result.error() == null ? "The scan failed" : result.error(), now);
        } else {
            applyReport(scan, result.data(), now);
        }
        scans.save(scan);
    }

    private void applyReport(Scan scan, JsonNode data, Instant now) {
        ScanData report;
        try {
            report = mapper.treeToValue(data, ScanData.class);
        } catch (JacksonException exception) {
            scan.fail("The scan report could not be read", now);
            return;
        }
        boolean valid = report != null
                && report.score() != null
                && report.score() >= 0
                && report.score() <= 100
                && report.grade() != null
                && report.grade().matches("[A-F]")
                && report.checks() != null
                && report.checks().isArray();
        if (!valid) {
            scan.fail("The scan report was invalid", now);
            return;
        }
        String checks = report.checks().toString();
        if (checks.length() > MAX_CHECKS_JSON_LENGTH) {
            scan.fail("The scan report was too large", now);
            return;
        }
        scan.succeed(report.score(), report.grade(), checks, now);
    }

    /** Marks scans that never received a result (worker crash, lost message) as failed. */
    @Transactional
    public int failStaleScans() {
        Instant now = clock.instant();
        List<Scan> stale = scans.findAllByStatusAndCreatedAtBefore(
                ScanStatus.QUEUED, now.minus(queueProperties.scanTimeout()));
        for (Scan scan : stale) {
            scan.fail("The scan timed out", now);
        }
        scans.saveAll(stale);
        return stale.size();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Scan not found");
    }
}
