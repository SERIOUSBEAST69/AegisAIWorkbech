package com.trustai.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class ApiMetricService {

    private static final int WINDOW_SIZE = 600;

    private final ConcurrentHashMap<String, ApiMetricWindow> metrics = new ConcurrentHashMap<>();

    public void record(String api, long durationMs, int statusCode) {
        metrics.computeIfAbsent(api, key -> new ApiMetricWindow()).record(durationMs, statusCode);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> apis = new ArrayList<>();
        metrics.forEach((api, window) -> apis.add(window.toPayload(api)));
        apis.sort(Comparator.comparing(item -> String.valueOf(item.get("api"))));
        result.put("generatedAt", Instant.now().toString());
        result.put("apis", apis);
        return result;
    }

    private static class ApiMetricWindow {
        private final AtomicLong total = new AtomicLong(0);
        private final AtomicLong success = new AtomicLong(0);
        private final AtomicLong fail = new AtomicLong(0);
        private final ArrayDeque<Long> latencies = new ArrayDeque<>();

        synchronized void record(long durationMs, int statusCode) {
            total.incrementAndGet();
            if (statusCode >= 200 && statusCode < 400) {
                success.incrementAndGet();
            } else {
                fail.incrementAndGet();
            }
            latencies.addLast(Math.max(durationMs, 0));
            while (latencies.size() > WINDOW_SIZE) {
                latencies.removeFirst();
            }
        }

        synchronized Map<String, Object> toPayload(String api) {
            List<Long> samples = new ArrayList<>(latencies);
            Collections.sort(samples);
            Map<String, Object> item = new HashMap<>();
            item.put("api", api);
            item.put("total", total.get());
            item.put("success", success.get());
            item.put("fail", fail.get());
            item.put("p50", percentile(samples, 0.50));
            item.put("p95", percentile(samples, 0.95));
            item.put("p99", percentile(samples, 0.99));
            item.put("max", samples.isEmpty() ? 0 : samples.get(samples.size() - 1));
            return item;
        }

        private long percentile(List<Long> sorted, double p) {
            if (sorted.isEmpty()) return 0;
            int idx = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * p) - 1);
            return sorted.get(Math.max(idx, 0));
        }
    }
}
