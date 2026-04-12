package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.config.jwt.JwtUtil;
import com.trustai.entity.SecurityEvent;
import com.trustai.entity.SimulationEvent;
import com.trustai.entity.User;
import com.trustai.service.ClientIngressAuthService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.EventHubService;
import com.trustai.service.SecurityEventService;
import com.trustai.service.SimulationEventService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import io.jsonwebtoken.Claims;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/simulation-events")
@Validated
@RequiredArgsConstructor
public class SimulationEventController {

    private final SimulationEventService simulationEventService;
    private final SecurityEventService securityEventService;
    private final EventHubService eventHubService;
    private final ClientIngressAuthService clientIngressAuthService;
    private final CompanyScopeService companyScopeService;
    private final CurrentUserService currentUserService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    private final ScheduledExecutorService sseScheduler = Executors.newScheduledThreadPool(1);

    @PostMapping("/trigger")
    public R<Map<String, Object>> trigger(@RequestHeader(value = "X-Client-Token", required = false) String clientToken,
                                          @RequestHeader(value = "X-Company-Id", required = false) Long headerCompanyId,
                                          @RequestBody(required = false) TriggerReq req) {
        if (!clientIngressAuthService.isAuthorized(clientToken)) {
            return R.error(40100, "客户端令牌无效");
        }
        TriggerReq safeReq = req == null ? new TriggerReq() : req;

        Long companyId = headerCompanyId;
        if (companyId == null || companyId <= 0) {
            companyId = safeReq.getCompanyId();
        }
        if (companyId == null || companyId <= 0) {
            companyId = clientIngressAuthService.getDefaultCompanyId();
        }

        String eventType = normalizeEventType(safeReq.getEventType());
        String severity = normalizeSeverity(safeReq.getSeverity());
        String targetKey = StringUtils.hasText(safeReq.getTargetKey()) ? safeReq.getTargetKey().trim() : "core-network";
        String source = StringUtils.hasText(safeReq.getSource()) ? safeReq.getSource().trim() : "client-trigger";
        String triggerUser = StringUtils.hasText(safeReq.getTriggerUser()) ? safeReq.getTriggerUser().trim() : "client";

        Map<String, Object> payload = safeReq.getPayload() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(safeReq.getPayload());
        String attackType = normalizeAttackType(
            firstNonNull(
                safeReq.getAttackType(),
                payload.get("attackType"),
                payload.get("attack_type"),
                eventType
            )
        );
        payload.putIfAbsent("eventType", eventType);
        payload.putIfAbsent("severity", severity);
        payload.putIfAbsent("targetKey", targetKey);
        payload.putIfAbsent("attackType", attackType);
        payload.putIfAbsent("source", source);
        payload.putIfAbsent("triggerUser", triggerUser);
        payload.putIfAbsent("ts", System.currentTimeMillis());

        SimulationEvent event = new SimulationEvent();
        event.setCompanyId(companyId);
        event.setEventType(eventType);
        event.setTargetKey(targetKey);
        event.setSeverity(severity);
        event.setStatus("pending");
        event.setSource(source);
        event.setTriggerUser(triggerUser);
        event.setPayloadJson(encodePayload(payload));
        event.setCreateTime(new Date());
        event.setUpdateTime(new Date());
        simulationEventService.save(event);

        Long securityEventId = null;
        Long governanceEventId = null;
        boolean generateAlert = safeReq.getGenerateAlert() == null || safeReq.getGenerateAlert();
        if (generateAlert) {
            User reporter = resolveReporter(companyId, triggerUser);
            if (reporter != null) {
                SecurityEvent securityEvent = new SecurityEvent();
                securityEvent.setCompanyId(companyId);
                securityEvent.setEmployeeId(reporter.getUsername());
                securityEvent.setEventType(mapToSecurityEventType(eventType));
                securityEvent.setFilePath("[simulation] " + targetKey);
                securityEvent.setTargetAddr("sim://" + targetKey);
                securityEvent.setHostname("simulation-engine");
                securityEvent.setSeverity(severity);
                securityEvent.setStatus("pending");
                securityEvent.setSource("simulation");
                securityEvent.setPolicyVersion(eventHubService.resolvePolicyVersion(companyId));
                securityEvent.setEventTime(new Date());
                securityEvent.setCreateTime(new Date());
                securityEvent.setUpdateTime(new Date());
                securityEventService.save(securityEvent);
                securityEventId = securityEvent.getId();
                var ge = eventHubService.ingestSecurityEvent(securityEvent, reporter, payload);
                governanceEventId = ge == null ? null : ge.getId();
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", event.getId());
        data.put("companyId", companyId);
        data.put("status", event.getStatus());
        data.put("securityEventId", securityEventId);
        data.put("governanceEventId", governanceEventId);
        return R.ok(data);
    }

    @GetMapping("/pending")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> pending(@RequestParam(defaultValue = "0") Long afterId,
                                          @RequestParam(defaultValue = "20") int limit) {
        Long companyId = companyScopeService.requireCompanyId();
        long safeAfter = afterId == null ? 0L : Math.max(0L, afterId);
        int safeLimit = Math.max(5, Math.min(100, limit));

        List<Map<String, Object>> items = loadPending(companyId, safeAfter, safeLimit);
        long cursor = items.stream()
            .map(item -> Long.valueOf(String.valueOf(item.getOrDefault("eventId", 0L))))
            .max(Long::compareTo)
            .orElse(safeAfter);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("cursor", Math.max(safeAfter, cursor));
        data.put("generatedAt", new Date());
        return R.ok(data);
    }

    @PostMapping("/mark-processed")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> markProcessed(@RequestBody MarkReq req) {
        Long companyId = companyScopeService.requireCompanyId();
        String operator = currentUserService.requireCurrentUser().getUsername();

        List<Long> ids = req == null || req.getEventIds() == null ? List.of() : req.getEventIds().stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (ids.isEmpty()) {
            return R.error(40000, "eventIds 不能为空");
        }

        List<SimulationEvent> events = simulationEventService.list(new QueryWrapper<SimulationEvent>()
            .eq("company_id", companyId)
            .in("id", ids));
        Date now = new Date();
        int updated = 0;
        for (SimulationEvent event : events) {
            if ("processed".equalsIgnoreCase(String.valueOf(event.getStatus()))) {
                continue;
            }
            event.setStatus("processed");
            event.setProcessedBy(operator);
            event.setProcessedAt(now);
            event.setUpdateTime(now);
            if (simulationEventService.updateById(event)) {
                updated += 1;
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("updated", updated);
        data.put("requested", ids.size());
        return R.ok(data);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("token") String token,
                             @RequestParam(defaultValue = "0") Long lastEventId,
                             @RequestParam(defaultValue = "20") int limit) {
        ViewerScope scope = scopeFromToken(token);
        long safeLast = lastEventId == null ? 0L : Math.max(0L, lastEventId);
        int safeLimit = Math.max(5, Math.min(80, limit));

        SseEmitter emitter = new SseEmitter(0L);
        final long[] cursor = { safeLast };

        Runnable publish = () -> {
            try {
                List<Map<String, Object>> updates = loadPending(scope.companyId(), cursor[0], safeLimit);
                if (!updates.isEmpty()) {
                    long maxId = updates.stream()
                        .map(item -> Long.valueOf(String.valueOf(item.get("eventId"))))
                        .max(Long::compareTo)
                        .orElse(cursor[0]);
                    cursor[0] = Math.max(cursor[0], maxId);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("items", updates);
                    payload.put("cursor", cursor[0]);
                    payload.put("generatedAt", new Date());
                    emitter.send(SseEmitter.event().name("simulation").data(payload));
                } else {
                    emitter.send(SseEmitter.event().name("ping").data(Map.of("ts", System.currentTimeMillis(), "cursor", cursor[0])));
                }
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        };

        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("cursor", cursor[0], "ts", System.currentTimeMillis())).reconnectTime(3000));
        } catch (Exception ex) {
            emitter.completeWithError(ex);
            return emitter;
        }

        publish.run();
        var future = sseScheduler.scheduleAtFixedRate(publish, 3, 3, TimeUnit.SECONDS);
        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> {
            future.cancel(true);
            emitter.complete();
        });
        emitter.onError(ex -> future.cancel(true));
        return emitter;
    }

    private List<Map<String, Object>> loadPending(Long companyId, long afterIdExclusive, int limit) {
        return simulationEventService.list(new QueryWrapper<SimulationEvent>()
                .eq("company_id", companyId)
                .eq("status", "pending")
                .gt("id", afterIdExclusive)
                .orderByAsc("id")
                .last("limit " + limit))
            .stream()
            .map(this::mapRow)
            .toList();
    }

    private Map<String, Object> mapRow(SimulationEvent event) {
        Map<String, Object> payload = decodePayload(event.getPayloadJson());
        String attackType = normalizeAttackType(firstNonNull(payload.get("attackType"), payload.get("attack_type"), event.getEventType()));
        String severity = normalizeSeverity(event.getSeverity());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("eventId", event.getId());
        row.put("eventType", event.getEventType());
        row.put("attackType", attackType);
        row.put("targetKey", event.getTargetKey());
        row.put("severity", severity);
        row.put("source", event.getSource());
        row.put("triggerUser", event.getTriggerUser());
        row.put("status", event.getStatus());
        row.put("effectProfile", resolveEffectProfile(attackType, severity, payload));
        row.put("payload", payload);
        row.put("createTime", event.getCreateTime());
        return row;
    }

    private String normalizeAttackType(Object value) {
        String raw = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(raw)) {
            return "GENERIC_ATTACK";
        }
        if (raw.contains("jailbreak") || raw.contains("prompt") || raw.contains("context")) {
            return "JAILBREAK_ATTEMPT";
        }
        if (raw.contains("poison")) {
            return "DATA_POISONING";
        }
        if (raw.contains("exfil") || raw.contains("leak") || raw.contains("credential")) {
            return "SENSITIVE_DATA_EXFILTRATION";
        }
        return raw.toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> resolveEffectProfile(String attackType, String severity, Map<String, Object> payload) {
        Object custom = firstNonNull(payload.get("effectProfile"), payload.get("effect_profile"));
        if (custom instanceof Map<?, ?> map && !map.isEmpty()) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return converted;
        }

        return switch (String.valueOf(attackType)) {
            case "JAILBREAK_ATTEMPT" -> Map.of(
                "theme", "jailbreak_beacon",
                "primaryColor", "#4AA8FF",
                "particlePreset", "prompt_shards",
                "beamPreset", "blue_pillar",
                "durationMs", 820,
                "intensity", "high",
                "severity", severity
            );
            case "DATA_POISONING" -> Map.of(
                "theme", "poison_wave",
                "primaryColor", "#27C66F",
                "particlePreset", "green_contaminate",
                "beamPreset", "toxic_diffusion",
                "durationMs", 920,
                "intensity", "medium",
                "severity", severity
            );
            case "SENSITIVE_DATA_EXFILTRATION" -> Map.of(
                "theme", "exfiltration_stream",
                "primaryColor", "#3BD7C8",
                "secondaryColor", "#FF5A5A",
                "particlePreset", "exfil_stream",
                "beamPreset", "data_leak_arc",
                "durationMs", 860,
                "intensity", "high",
                "severity", severity
            );
            default -> Map.of(
                "theme", "default_alert",
                "primaryColor", "critical".equals(severity) ? "#FF213F" : "#FF5F3D",
                "particlePreset", "default_burst",
                "beamPreset", "default_trace",
                "durationMs", 680,
                "intensity", "medium",
                "severity", severity
            );
        };
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private User resolveReporter(Long companyId, String triggerUser) {
        if (StringUtils.hasText(triggerUser)) {
            User user = userService.lambdaQuery()
                .eq(User::getCompanyId, companyId)
                .eq(User::getUsername, triggerUser.trim())
                .eq(User::getAccountStatus, "active")
                .one();
            if (user != null) {
                return user;
            }
        }
        return userService.lambdaQuery()
            .eq(User::getCompanyId, companyId)
            .eq(User::getAccountStatus, "active")
            .last("limit 1")
            .one();
    }

    private ViewerScope scopeFromToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new IllegalArgumentException("token 不能为空");
        }
        String token = rawToken.trim();
        if (token.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            token = token.substring(7).trim();
        }
        Claims claims = jwtUtil.parse(token);
        Long userId = claims.get("uid", Long.class);
        Long companyId = claims.get("cid", Long.class);
        if (userId == null || companyId == null) {
            throw new IllegalArgumentException("token 缺少 uid/cid 信息");
        }
        User user = userService.getById(userId);
        if (user == null || !Objects.equals(user.getCompanyId(), companyId)) {
            throw new IllegalArgumentException("token 用户无效");
        }
        return new ViewerScope(companyId, user.getId());
    }

    private String normalizeEventType(String value) {
        String raw = String.valueOf(value == null ? "" : value).trim().toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(raw)) {
            return "ATTACK_PATH";
        }
        if (Set.of("ATTACK_PATH", "SHIELD_HIT", "DEPT_ALERT", "DATA_BURST").contains(raw)) {
            return raw;
        }
        return "ATTACK_PATH";
    }

    private String normalizeSeverity(String value) {
        String raw = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        if (Set.of("critical", "high", "medium", "low").contains(raw)) {
            return raw;
        }
        return "high";
    }

    private String mapToSecurityEventType(String eventType) {
        return switch (String.valueOf(eventType)) {
            case "SHIELD_HIT" -> "SUSPICIOUS_UPLOAD";
            case "DEPT_ALERT" -> "DATA_SCRAPE";
            case "DATA_BURST" -> "EXFILTRATION";
            default -> "FILE_STEAL";
        };
    }

    private String encodePayload(Map<String, Object> payload) {
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> decodePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return Map.of();
        }
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readValue(payloadJson, Map.class);
        } catch (Exception ex) {
            return Map.of("raw", payloadJson);
        }
    }

    private record ViewerScope(Long companyId, Long userId) {}

    public static class TriggerReq {
        private Long companyId;
        private String eventType;
        private String attackType;
        private String targetKey;
        private String severity;
        private String source;
        private String triggerUser;
        private Boolean generateAlert;
        private Map<String, Object> payload;

        public Long getCompanyId() { return companyId; }
        public void setCompanyId(Long companyId) { this.companyId = companyId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getAttackType() { return attackType; }
        public void setAttackType(String attackType) { this.attackType = attackType; }
        public String getTargetKey() { return targetKey; }
        public void setTargetKey(String targetKey) { this.targetKey = targetKey; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getTriggerUser() { return triggerUser; }
        public void setTriggerUser(String triggerUser) { this.triggerUser = triggerUser; }
        public Boolean getGenerateAlert() { return generateAlert; }
        public void setGenerateAlert(Boolean generateAlert) { this.generateAlert = generateAlert; }
        public Map<String, Object> getPayload() { return payload; }
        public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    }

    public static class MarkReq {
        @NotEmpty
        private List<Long> eventIds;

        public List<Long> getEventIds() { return eventIds; }
        public void setEventIds(List<Long> eventIds) { this.eventIds = eventIds; }
    }
}
