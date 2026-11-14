package io.github.yatab1.vigie.scan;

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
@Table(name = "scans")
public class Scan {

    private static final int MAX_ERROR_LENGTH = 500;

    /** Also used as the job identifier sent to the worker. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "domain_id", nullable = false)
    private UUID domainId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ScanStatus status;

    private Integer score;

    @Column(length = 2)
    private String grade;

    /** JSON array of check results, stored exactly as validated from the worker. */
    @Column(columnDefinition = "text")
    private String checks;

    @Column(length = MAX_ERROR_LENGTH)
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected Scan() {
        // Required by JPA.
    }

    public Scan(UUID domainId, Instant createdAt) {
        this.domainId = domainId;
        this.status = ScanStatus.QUEUED;
        this.createdAt = createdAt;
    }

    public boolean isQueued() {
        return status == ScanStatus.QUEUED;
    }

    public void succeed(int score, String grade, String checks, Instant now) {
        this.status = ScanStatus.SUCCEEDED;
        this.score = score;
        this.grade = grade;
        this.checks = checks;
        this.finishedAt = now;
    }

    public void fail(String error, Instant now) {
        this.status = ScanStatus.FAILED;
        this.error = error == null || error.length() <= MAX_ERROR_LENGTH
                ? error
                : error.substring(0, MAX_ERROR_LENGTH);
        this.finishedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDomainId() {
        return domainId;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public Integer getScore() {
        return score;
    }

    public String getGrade() {
        return grade;
    }

    public String getChecks() {
        return checks;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
