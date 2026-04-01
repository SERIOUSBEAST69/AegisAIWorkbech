package com.trustai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceabilityAndObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean(name = "securitySchemaInitializer")
    private CommandLineRunner securitySchemaInitializerRunner;

    @MockBean(name = "privacyShieldSchemaInitializer")
    private CommandLineRunner privacyShieldSchemaInitializerRunner;

    @MockBean(name = "awardSchemaInitializer")
    private CommandLineRunner awardSchemaInitializerRunner;

    @Test
    void aiRiskDetailShouldExposeFiveScoreDimensions() throws Exception {
        String token = loginAndGetToken("secops", "Passw0rd!");
        JsonNode resp = getJson("/api/ai-risk/score?service=tongyi", token, status().isOk());
        assertEquals(20000, resp.path("code").asInt());

        JsonNode scores = resp.path("data").path("scores");
        assertTrue(scores.isObject());
        assertEquals(5, scores.size());
        assertTrue(scores.has("base_risk"));
        assertTrue(scores.has("privacy_exposure"));
        assertTrue(scores.has("usage_volume"));
        assertTrue(scores.has("failure_rate"));
        assertTrue(scores.has("latency"));
    }

    @Test
    void rejectUntraceableSecurityReportWrite() throws Exception {
        JsonNode resp = postJson(
            "/api/security/events/report",
            null,
            Map.of(
                "eventType", "FILE_STEAL",
                "employeeId", "system",
                "hostname", "HOST-FAKE",
                "severity", "high"
            ),
            status().isOk()
        );
        assertEquals(40000, resp.path("code").asInt());
    }

    @Test
    void adminVisibleSecurityIncidentMustBeVisibleToSubject() throws Exception {
        JsonNode reportResp = postJson(
            "/api/security/events/report",
            null,
            Map.of(
                "eventType", "FILE_STEAL",
                "employeeId", "employee1",
                "hostname", "HOST-EMPLOYEE1",
                "filePath", "C:/tmp/customer.csv",
                "targetAddr", "https://x.example/upload",
                "severity", "high"
            ),
            status().isOk()
        );
        assertEquals(20000, reportResp.path("code").asInt());
        String sourceEventId = String.valueOf(reportResp.path("data").path("id").asLong());

        String adminToken = loginAndGetToken("admin", "admin");
        JsonNode adminList = getJson("/api/alert-center/list?page=1&pageSize=50", adminToken, status().isOk());
        boolean adminCanSee = containsSourceEvent(adminList.path("data").path("list"), sourceEventId);
        assertTrue(adminCanSee, "admin should see governance event linked to security incident");

        String employeeToken = loginAndGetToken("employee1", "Passw0rd!");
        JsonNode employeeList = getJson("/api/alert-center/list?page=1&pageSize=50", employeeToken, status().isOk());
        boolean employeeCanSee = containsSourceEvent(employeeList.path("data").path("list"), sourceEventId);
        assertTrue(employeeCanSee, "subject employee should see same linked incident in personal view");
    }

    @Test
    void adminVisibleShadowAiClientMustBeVisibleToSubjectEmployee() throws Exception {
        String uniqueClientId = "trace-client-" + System.currentTimeMillis();
        JsonNode reportResp = postClientJson(
            "/api/client/report",
            Map.of(
                "clientId", uniqueClientId,
                "hostname", "HOST-TRACE-EMPLOYEE1",
                "osUsername", "employee1",
                "osType", "Windows",
                "clientVersion", "1.0.0",
                "discoveredServices", "[]",
                "shadowAiCount", 1
            ),
            status().isOk()
        );
        assertEquals(20000, reportResp.path("code").asInt(), reportResp.toString());

        String adminToken = loginAndGetToken("admin", "admin");
        JsonNode adminList = getJson("/api/client/list", adminToken, status().isOk());
        assertTrue(containsClientId(adminList.path("data"), uniqueClientId), "admin should see reported client");

        String employeeToken = loginAndGetToken("employee1", "Passw0rd!");
        JsonNode employeeList = getJson("/api/client/list", employeeToken, status().isOk());
        assertTrue(containsClientId(employeeList.path("data"), uniqueClientId), "subject employee should see own reported client");
    }

    @Test
    void workbenchTrendShouldContainNonZeroSeriesForAdminAndSecops() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin");
        JsonNode adminResp = getJson("/api/dashboard/workbench", adminToken, status().isOk());
        JsonNode adminTrend = adminResp.path("data").path("trend");
        assertTrue(hasNonZero(adminTrend.path("riskSeries")) || hasNonZero(adminTrend.path("auditSeries")) || hasNonZero(adminTrend.path("aiCallSeries")));

        String secopsToken = loginAndGetToken("secops", "Passw0rd!");
        JsonNode secopsResp = getJson("/api/dashboard/workbench", secopsToken, status().isOk());
        JsonNode secopsTrend = secopsResp.path("data").path("trend");
        assertTrue(hasNonZero(secopsTrend.path("riskSeries")) || hasNonZero(secopsTrend.path("auditSeries")) || hasNonZero(secopsTrend.path("aiCallSeries")));
    }

    private boolean hasNonZero(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return false;
        }
        for (JsonNode item : arrayNode) {
            if (item.asDouble(0D) > 0D) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSourceEvent(JsonNode listNode, String sourceEventId) {
        if (!listNode.isArray()) {
            return false;
        }
        for (JsonNode item : listNode) {
            if (sourceEventId.equals(item.path("sourceEventId").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsClientId(JsonNode listNode, String clientId) {
        if (!listNode.isArray()) {
            return false;
        }
        for (JsonNode item : listNode) {
            if (clientId.equals(item.path("clientId").asText())) {
                return true;
            }
        }
        return false;
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        JsonNode loginResp = postJson(
            "/api/auth/login",
            null,
            Map.of("username", username, "password", password),
            status().isOk()
        );
        assertEquals(20000, loginResp.path("code").asInt());
        String token = loginResp.path("data").path("token").asText();
        assertNotNull(token);
        assertFalse(token.isBlank());
        return token;
    }

    private JsonNode postJson(String path,
                              String token,
                              Object body,
                              org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        var builder = post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(matcher)
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path,
                             String token,
                             org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        var builder = get(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(matcher)
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postClientJson(String path,
                                    Object body,
                                    org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        MvcResult result = mockMvc.perform(
                post(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Token", "demo-client-token")
                    .header("X-Company-Id", "1")
                    .content(objectMapper.writeValueAsString(body))
            )
            .andExpect(matcher)
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
