package io.github.yatab1.vigie.domain;

import java.time.Instant;
import java.util.UUID;

public record DomainResponse(
        UUID id,
        String hostname,
        DomainStatus status,
        Verification verification,
        Instant verifiedAt,
        Instant createdAt) {

    /** The DNS record to publish, and the state of the last check. */
    public record Verification(
            String recordType, String recordName, String recordValue, boolean checking, String message) {
    }

    public static DomainResponse from(Domain domain) {
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
                domain.getCreatedAt());
    }
}
