package io.github.yatab1.vigie.support;

import io.github.yatab1.vigie.queue.JobQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Test double for the Redis queue: remembers the jobs instead of sending them. */
public class RecordingJobQueue implements JobQueue {

    public record Job(UUID id, String type, Map<String, Object> payload) {
    }

    private final List<Job> jobs = new ArrayList<>();

    @Override
    public synchronized void enqueue(UUID jobId, String type, Map<String, Object> payload) {
        jobs.add(new Job(jobId, type, payload));
    }

    public synchronized List<Job> jobs() {
        return List.copyOf(jobs);
    }

    public synchronized Job last() {
        return jobs.get(jobs.size() - 1);
    }

    public synchronized void clear() {
        jobs.clear();
    }
}
