package io.github.yatab1.vigie.status;

/** Body of {@code GET /api/v1/status}. */
public record StatusResponse(String service, String status, String version) {
}
