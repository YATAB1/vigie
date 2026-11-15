package io.github.yatab1.vigie.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainRepository extends JpaRepository<Domain, UUID> {

    List<Domain> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Lookup scoped to the owner: another user's domain is simply not found. */
    Optional<Domain> findByIdAndUserId(UUID id, UUID userId);

    Optional<Domain> findByVerificationJobId(UUID verificationJobId);

    List<Domain> findAllByStatus(DomainStatus status);

    boolean existsByUserIdAndHostname(UUID userId, String hostname);

    long countByUserId(UUID userId);
}
