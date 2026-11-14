package io.github.yatab1.vigie.scan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanRepository extends JpaRepository<Scan, UUID> {

    List<Scan> findTop20ByDomainIdOrderByCreatedAtDesc(UUID domainId);

    boolean existsByDomainIdAndStatus(UUID domainId, ScanStatus status);

    List<Scan> findAllByStatusAndCreatedAtBefore(ScanStatus status, Instant cutoff);
}
