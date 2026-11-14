package io.github.yatab1.vigie.queue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/** A message published by the worker on the results queue. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkerResult(
        @JsonProperty("job_id") String jobId,
        String type,
        String status,
        JsonNode data,
        String error) {

    public boolean succeeded() {
        return "succeeded".equals(status) && data != null && !data.isNull();
    }
}
