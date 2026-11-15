package io.github.yatab1.vigie.auth;

import io.github.yatab1.vigie.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limits failed sign-ins per account: after {@value #MAX_FAILURES} failures in
 * {@link #WINDOW}, the account refuses sign-ins until the oldest failure expires.
 *
 * <p>Keyed by the normalized email, whether or not an account exists, so the
 * limit itself does not reveal which emails are registered. State is in memory:
 * with several API instances, each one counts separately.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** Throws 429 if this email has too many recent failures. */
    public void check(String email) {
        Instant now = clock.instant();
        Deque<Instant> recent = failures.get(email);
        if (recent == null) {
            return;
        }
        synchronized (recent) {
            prune(recent, now);
            if (recent.size() >= MAX_FAILURES) {
                long minutes = Math.max(1, Duration.between(now, recent.peekFirst().plus(WINDOW)).toMinutes() + 1);
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many failed sign-ins, try again in " + minutes + " minutes");
            }
        }
    }

    public void recordFailure(String email) {
        Instant now = clock.instant();
        Deque<Instant> recent = failures.computeIfAbsent(email, key -> new ArrayDeque<>());
        synchronized (recent) {
            prune(recent, now);
            recent.addLast(now);
        }
    }

    public void reset(String email) {
        failures.remove(email);
    }

    /** Forgets accounts whose failures have all expired, so memory does not grow forever. */
    @Scheduled(fixedDelayString = "PT10M")
    public void evictExpired() {
        Instant now = clock.instant();
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }

    void clear() {
        failures.clear();
    }

    private static void prune(Deque<Instant> recent, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
            recent.pollFirst();
        }
    }
}
