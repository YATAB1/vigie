package io.github.yatab1.vigie.domain;

import io.github.yatab1.vigie.common.ApiException;
import io.github.yatab1.vigie.queue.JobQueue;
import io.github.yatab1.vigie.queue.WorkerResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class DomainService {

    private static final Logger log = LoggerFactory.getLogger(DomainService.class);
    private static final int MAX_DOMAINS_PER_USER = 20;
    private static final int TOKEN_BYTES = 24;

    private final DomainRepository domains;
    private final JobQueue jobQueue;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public DomainService(DomainRepository domains, JobQueue jobQueue, ObjectMapper mapper, Clock clock) {
        this.domains = domains;
        this.jobQueue = jobQueue;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<Domain> list(UUID userId) {
        return domains.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Returns the domain only if it belongs to the user. A domain owned by someone
     * else gives the same 404 as a domain that does not exist, so identifiers
     * cannot be probed.
     */
    public Domain get(UUID userId, UUID domainId) {
        return domains.findByIdAndUserId(domainId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Domain not found"));
    }

    @Transactional
    public Domain create(UUID userId, String rawHostname) {
        String hostname = Hostnames.normalize(rawHostname)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "Enter a public domain name, such as example.com"));
        if (domains.existsByUserIdAndHostname(userId, hostname)) {
            throw alreadyAdded();
        }
        if (domains.countByUserId(userId) >= MAX_DOMAINS_PER_USER) {
            throw new ApiException(HttpStatus.CONFLICT, "Domain limit reached");
        }
        byte[] token = new byte[TOKEN_BYTES];
        random.nextBytes(token);
        try {
            return domains.saveAndFlush(
                    new Domain(userId, hostname, HexFormat.of().formatHex(token), clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            throw alreadyAdded();
        }
    }

    @Transactional
    public void delete(UUID userId, UUID domainId) {
        domains.delete(get(userId, domainId));
    }

    /**
     * Asks the worker to read the challenge TXT record. The answer arrives later
     * through {@link #completeVerification}.
     */
    public Domain requestVerification(UUID userId, UUID domainId) {
        Domain domain = get(userId, domainId);
        if (domain.isVerified()) {
            return domain;
        }
        UUID jobId = UUID.randomUUID();
        domain.startVerification(jobId);
        domain = domains.save(domain);
        try {
            jobQueue.enqueue(jobId, "dns_txt", Map.of("name", domain.challengeRecordName()));
        } catch (RuntimeException exception) {
            log.error("Cannot enqueue verification job", exception);
            domain.verificationFailed("Verification is temporarily unavailable");
            domains.save(domain);
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Verification is temporarily unavailable");
        }
        return domain;
    }

    @Transactional
    public void completeVerification(UUID jobId, WorkerResult result) {
        Domain domain = domains.findByVerificationJobId(jobId).orElse(null);
        if (domain == null || domain.isVerified()) {
            return;
        }
        if (!result.succeeded()) {
            domain.verificationFailed("The DNS lookup failed, try again in a few minutes");
        } else if (challengePublished(domain, result)) {
            domain.markVerified(clock.instant());
            log.info("Domain {} verified", domain.getId());
        } else {
            domain.verificationFailed(
                    "TXT record not found yet. DNS changes can take a few minutes to appear");
        }
        domains.save(domain);
    }

    /** The decision is made here, in the API: the worker only reports what DNS contains. */
    private boolean challengePublished(Domain domain, WorkerResult result) {
        DnsTxtData data;
        try {
            data = mapper.treeToValue(result.data(), DnsTxtData.class);
        } catch (JacksonException exception) {
            return false;
        }
        if (data == null || data.records() == null) {
            return false;
        }
        byte[] expected = domain.challengeRecordValue().getBytes(StandardCharsets.UTF_8);
        boolean found = false;
        for (String record : data.records()) {
            if (record != null
                    && MessageDigest.isEqual(expected, record.trim().getBytes(StandardCharsets.UTF_8))) {
                found = true;
            }
        }
        return found;
    }

    private static ApiException alreadyAdded() {
        return new ApiException(HttpStatus.CONFLICT, "You already added this domain");
    }
}
