package io.github.yatab1.vigie.queue;

import java.util.Map;
import java.util.UUID;

/** Sends work to the scan worker. The API itself never contacts a user's domain. */
public interface JobQueue {

    void enqueue(UUID jobId, String type, Map<String, Object> payload);
}
