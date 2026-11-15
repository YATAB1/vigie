package io.github.yatab1.vigie.scan;

import java.util.UUID;

/** Published when a scan finishes successfully, after the transaction commits. */
public record ScanCompletedEvent(UUID scanId, UUID domainId) {
}
