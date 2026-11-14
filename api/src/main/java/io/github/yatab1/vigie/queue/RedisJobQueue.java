package io.github.yatab1.vigie.queue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisJobQueue implements JobQueue {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final QueueProperties properties;

    public RedisJobQueue(StringRedisTemplate redis, ObjectMapper mapper, QueueProperties properties) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public void enqueue(UUID jobId, String type, Map<String, Object> payload) {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("id", jobId.toString());
        job.put("type", type);
        job.put("payload", payload);
        redis.opsForList().rightPush(properties.jobsKey(), mapper.writeValueAsString(job));
    }
}
