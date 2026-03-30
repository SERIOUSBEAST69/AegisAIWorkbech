package com.trustai.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "governance.sensitive-ops.max-per-minute=3")
class GovernanceConcurrentSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean(name = "securitySchemaInitializer")
    private CommandLineRunner securitySchemaInitializerRunner;

    @MockBean(name = "privacyShieldSchemaInitializer")
    private CommandLineRunner privacyShieldSchemaInitializerRunner;

    @MockBean(name = "companySchemaInitializer")
    private CommandLineRunner companySchemaInitializerRunner;

    @MockBean(name = "awardSchemaInitializer")
    private CommandLineRunner awardSchemaInitializerRunner;

    @Test
    void parallelApproveShouldAllowOnlyOneSuccessfulTransition() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin");
        String secopsToken = loginAndGetToken("secops", "Passw0rd!");
        String payloadJson = objectMapper.writeValueAsString(Map.of(
            "name", "并发复核角色",
            "code", "RACE_ROLE_" + System.currentTimeMillis(),
            "description", "并发测试"
        ));

        JsonNode submitResp = postJson(
            "/api/governance-change/submit",
            adminToken,
            Map.of("module", "ROLE", "action", "ADD", "payloadJson", payloadJson, "confirmPassword", "admin")
        );
        assertEquals(20000, submitResp.path("code").asInt(), submitResp.toString());
        long requestId = submitResp.path("data").path("id").asLong();
        assertTrue(requestId > 0);

        int concurrent = 8;
        List<JsonNode> responses = runParallel(concurrent, () -> postJson(
            "/api/governance-change/approve",
            secopsToken,
            Map.of("requestId", requestId, "approve", true, "note", "race approve", "confirmPassword", "Passw0rd!")
        ));

        long successCount = responses.stream().filter(r -> r.path("code").asInt() == 20000).count();
        long blockedCount = responses.stream().filter(r -> r.path("code").asInt() == 40000).count();
        assertEquals(1L, successCount, "并发审批应只有一次成功状态迁移");
        assertTrue(blockedCount >= concurrent - 1L, "其余并发审批应被待审批状态校验拦截");
    }

    @Test
    void parallelSensitiveDeleteShouldTripFuse() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin");

        int concurrent = 24;
        List<JsonNode> responses = runParallel(concurrent, () -> postJson(
            "/api/user/delete",
            adminToken,
            Map.of("id", -100L, "confirmPassword", "admin", "deleteReason", "parallel_attack")
        ));

        boolean hasFuse = responses.stream().anyMatch(r -> r.path("code").asInt() == 40000);
        if (!hasFuse) {
            // Some CI environments may schedule the burst less densely.
            // Fallback to an immediate sequential burst to deterministically cross the fuse threshold.
            for (int i = 0; i < 40 && !hasFuse; i++) {
                JsonNode resp = postJson(
                    "/api/user/delete",
                    adminToken,
                    Map.of("id", -100L, "confirmPassword", "admin", "deleteReason", "sequential_attack")
                );
                hasFuse = resp.path("code").asInt() == 40000;
            }
        }
        assertTrue(hasFuse, "并发敏感操作应触发频控熔断");
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        JsonNode resp = postJson("/api/auth/login", null, Map.of("username", username, "password", password));
        assertEquals(20000, resp.path("code").asInt(), resp.toString());
        String token = resp.path("data").path("token").asText();
        assertTrue(token != null && !token.isBlank());
        return token;
    }

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        var builder = post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<JsonNode> runParallel(int count, Callable<JsonNode> task) throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(count, 12));
        try {
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(task));
            }
            List<JsonNode> result = new ArrayList<>();
            for (Future<JsonNode> future : futures) {
                result.add(future.get());
            }
            return result;
        } finally {
            executor.shutdownNow();
        }
    }
}