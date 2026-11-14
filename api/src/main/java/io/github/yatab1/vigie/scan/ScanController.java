package io.github.yatab1.vigie.scan;

import io.github.yatab1.vigie.auth.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ScanController {

    private final ScanService scans;

    public ScanController(ScanService scans) {
        this.scans = scans;
    }

    /** Starts a scan. The client then polls GET /scans/{id} for the report. */
    @PostMapping("/domains/{domainId}/scans")
    public ResponseEntity<ScanResponse> start(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID domainId) {
        Scan scan = scans.request(user.id(), domainId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ScanResponse.summary(scan));
    }

    @GetMapping("/domains/{domainId}/scans")
    public List<ScanResponse> history(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID domainId) {
        return scans.list(user.id(), domainId).stream().map(ScanResponse::summary).toList();
    }

    @GetMapping("/scans/{id}")
    public ScanResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        Scan scan = scans.get(user.id(), id);
        return ScanResponse.detailed(scan, scans.checksOf(scan));
    }
}
