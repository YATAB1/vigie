package io.github.yatab1.vigie.domain;

import io.github.yatab1.vigie.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/domains")
public class DomainController {

    private final DomainService domains;

    public DomainController(DomainService domains) {
        this.domains = domains;
    }

    @GetMapping
    public List<DomainResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return domains.list(user.id()).stream().map(DomainResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<DomainResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateDomainRequest request) {
        Domain domain = domains.create(user.id(), request.hostname());
        return ResponseEntity.status(HttpStatus.CREATED).body(DomainResponse.from(domain));
    }

    @GetMapping("/{id}")
    public DomainResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return DomainResponse.from(domains.get(user.id(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        domains.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** Starts an ownership check. The client then polls GET /{id} for the outcome. */
    @PostMapping("/{id}/verify")
    public ResponseEntity<DomainResponse> verify(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        Domain domain = domains.requestVerification(user.id(), id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DomainResponse.from(domain));
    }
}
