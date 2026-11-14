package io.github.yatab1.vigie.queue;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Background thread that waits for worker results on Redis. */
@Component
public class ResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResultConsumer.class);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);
    private static final long RETRY_DELAY_MILLIS = 2000;

    private final StringRedisTemplate redis;
    private final ResultHandler handler;
    private final QueueProperties properties;

    private volatile boolean running;
    private Thread thread;

    public ResultConsumer(StringRedisTemplate redis, ResultHandler handler, QueueProperties properties) {
        this.redis = redis;
        this.handler = handler;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.consumerEnabled()) {
            return;
        }
        running = true;
        thread = new Thread(this::run, "vigie-result-consumer");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void run() {
        log.info("Listening for worker results on {}", properties.resultsKey());
        while (running) {
            String message;
            try {
                message = redis.opsForList().leftPop(properties.resultsKey(), POLL_TIMEOUT);
            } catch (RuntimeException exception) {
                if (!running) {
                    return;
                }
                log.warn("Cannot read worker results from Redis, retrying: {}", exception.getMessage());
                pause();
                continue;
            }
            if (message == null) {
                continue;
            }
            try {
                handler.handle(message);
            } catch (RuntimeException exception) {
                // One bad result must not stop the consumer.
                log.error("Failed to apply a worker result", exception);
            }
        }
    }

    private void pause() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
