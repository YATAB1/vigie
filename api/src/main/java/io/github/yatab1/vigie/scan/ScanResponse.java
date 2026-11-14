package io.github.yatab1.vigie.scan;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** A scan report. {@code checks} is only filled in the detailed view. */
public record ScanResponse(
        UUID id,
        UUID domainId,
        ScanStatus status,
        Integer score,
        String grade,
        String error,
        Instant createdAt,
        Instant finishedAt,
        JsonNode checks) {

    public static ScanResponse summary(Scan scan) {
        return detailed(scan, null);
    }

    public static ScanResponse detailed(Scan scan, JsonNode checks) {
        return new ScanResponse(
                scan.getId(),
                scan.getDomainId(),
                scan.getStatus(),
                scan.getScore(),
                scan.getGrade(),
                scan.getError(),
                scan.getCreatedAt(),
                scan.getFinishedAt(),
                checks);
    }
}
