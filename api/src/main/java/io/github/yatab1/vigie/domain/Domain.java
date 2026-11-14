package io.github.yatab1.vigie.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "domains")
public class Domain {

    public static final String CHALLENGE_LABEL = "_vigie-challenge";
    public static final String CHALLENGE_PREFIX = "vigie-verification=";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 253)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DomainStatus status;

    @Column(name = "verification_token", nullable = false, length = 64)
    private String verificationToken;

    @Column(name = "verification_job_id")
    private UUID verificationJobId;

    @Column(name = "verification_message", length = 300)
    private String verificationMessage;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Domain() {
        // Required by JPA.
    }

    public Domain(UUID userId, String hostname, String verificationToken, Instant createdAt) {
        this.userId = userId;
        this.hostname = hostname;
        this.verificationToken = verificationToken;
        this.status = DomainStatus.PENDING;
        this.createdAt = createdAt;
    }

    /** DNS name where the owner must publish the challenge. */
    public String challengeRecordName() {
        return CHALLENGE_LABEL + "." + hostname;
    }

    /** Exact TXT value that proves ownership. */
    public String challengeRecordValue() {
        return CHALLENGE_PREFIX + verificationToken;
    }

    public boolean isVerified() {
        return status == DomainStatus.VERIFIED;
    }

    public void startVerification(UUID jobId) {
        this.verificationJobId = jobId;
        this.verificationMessage = null;
    }

    public void markVerified(Instant now) {
        this.status = DomainStatus.VERIFIED;
        this.verifiedAt = now;
        this.verificationJobId = null;
        this.verificationMessage = null;
    }

    public void verificationFailed(String message) {
        this.verificationJobId = null;
        this.verificationMessage = message;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getHostname() {
        return hostname;
    }

    public DomainStatus getStatus() {
        return status;
    }

    public UUID getVerificationJobId() {
        return verificationJobId;
    }

    public String getVerificationMessage() {
        return verificationMessage;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
