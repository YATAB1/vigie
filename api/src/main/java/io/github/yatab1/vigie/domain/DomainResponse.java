package io.github.yatab1.vigie.domain;

import io.github.yatab1.vigie.scan.Scan;
import io.github.yatab1.vigie.scan.ScanResponse;
import java.time.Instant;
import java.util.UUID;

public record DomainResponse(
        UUID id,
        String hostname,
        DomainStatus status,
        Verification verification,
        Instant verifiedAt,
        Instant createdAt,
        ScanResponse latestScan) {

    /** The DNS record to publish, and the state of the last check. */
    public record Verification(
            String recordType, String recordName, String recordValue, boolean checking, String message) {
    }

    public static DomainResponse from(Domain domain) {
        return from(domain, null);
    }

    /** {@code latestScan} is the most recent successful scan, or null if there is none. */
    public static DomainResponse from(Domain domain, Scan latestScan) {
        Verification verification = new Verification(
                "TXT",
                domain.challengeRecordName(),
                domain.challengeRecordValue(),
                domain.getVerificationJobId() != null,
                domain.getVerificationMessage());
        return new DomainResponse(
                domain.getId(),
                domain.getHostname(),
                domain.getStatus(),
                verification,
                domain.getVerifiedAt(),
                domain.getCreatedAt(),
                latestScan == null ? null : ScanResponse.summary(latestScan));
    }
}
