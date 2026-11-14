package io.github.yatab1.vigie.queue;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vigie.queue")
public record QueueProperties(
        String jobsKey, String resultsKey, boolean consumerEnabled, Duration scanTimeout) {
}
