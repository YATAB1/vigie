package io.github.yatab1.vigie.queue;

import io.github.yatab1.vigie.domain.DomainService;
import io.github.yatab1.vigie.scan.ScanService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Applies a worker result to the database. Results are matched to a job the API
 * itself created and that is still waiting: anything else is ignored, so a forged
 * or replayed message cannot mark a domain as verified or overwrite a report.
 */
@Service
public class ResultHandler {

    private static final Logger log = LoggerFactory.getLogger(ResultHandler.class);

    private final ObjectMapper mapper;
    private final DomainService domains;
    private final ScanService scans;

    public ResultHandler(ObjectMapper mapper, DomainService domains, ScanService scans) {
        this.mapper = mapper;
        this.domains = domains;
        this.scans = scans;
    }

    public void handle(String rawMessage) {
        WorkerResult result;
        UUID jobId;
        try {
            result = mapper.readValue(rawMessage, WorkerResult.class);
            jobId = UUID.fromString(result.jobId());
        } catch (JacksonException | IllegalArgumentException | NullPointerException exception) {
            log.warn("Ignoring unreadable worker result");
            return;
        }
        if ("scan".equals(result.type())) {
            scans.complete(jobId, result);
        } else if ("dns_txt".equals(result.type())) {
            domains.completeVerification(jobId, result);
        }
    }
}
