package com.trustai.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class KeyTaskMetricService {

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public void record(String taskCode, boolean success) {
        counters.computeIfAbsent(taskCode, key -> new Counter()).record(success);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> tasks = new HashMap<>();
        counters.forEach((task, counter) -> tasks.put(task, counter.toPayload()));
        payload.put("generatedAt", Instant.now().toString());
        payload.put("tasks", tasks);
        return payload;
    }

    private static class Counter {
        private final AtomicLong total = new AtomicLong(0);
        private final AtomicLong success = new AtomicLong(0);

        void record(boolean ok) {
            total.incrementAndGet();
            if (ok) {
                success.incrementAndGet();
            }
        }

        Map<String, Object> toPayload() {
            Map<String, Object> data = new HashMap<>();
            long t = total.get();
            long s = success.get();
            data.put("total", t);
            data.put("success", s);
            data.put("fail", Math.max(0, t - s));
            data.put("successRate", t == 0 ? 0.0 : Math.round((s * 10000.0 / t)) / 100.0);
            return data;
        }
    }
}
