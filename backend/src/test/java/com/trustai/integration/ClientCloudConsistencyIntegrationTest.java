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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientCloudConsistencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean(name = "securitySchemaInitializer")
    private CommandLineRunner securitySchemaInitializerRunner;

    @MockBean(name = "privacyShieldSchemaInitializer")
    private CommandLineRunner privacyShieldSchemaInitializerRunner;

    @MockBean(name = "awardSchemaInitializer")
    private CommandLineRunner awardSchemaInitializerRunner;

    @Test
    void shadowAiReportShouldReturnAndPersistGovernanceLinkage() throws Exception {
        String uniqueClientId = "consistency-client-" + (System.currentTimeMillis() % 100000);
        JsonNode reportResp = postPublicJson(
            "/api/client/report",
            Map.of(
                "clientId", uniqueClientId,
                "hostname", "HOST-CONSISTENCY-EMPLOYEE1",
                "osUsername", "employee1",
                "osType", "Windows",
                "clientVersion", "1.0.0",
                "discoveredServices", "[]",
                "shadowAiCount", 2
            ),
            status().isOk()
        );
        assertEquals(20000, reportResp.path("code").asInt());

        long reportId = reportResp.path("data").path("id").asLong();
        long governanceEventId = reportResp.path("data").path("governanceEventId").asLong();
        long subjectUserId = reportResp.path("data").path("subjectUserId").asLong();
        assertTrue(reportId > 0);
        assertTrue(governanceEventId > 0);
        assertTrue(subjectUserId > 0);

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT user_id, username, source_module, source_event_id FROM governance_event WHERE id = ?",
            governanceEventId
        );
        assertEquals(subjectUserId, ((Number) row.get("user_id")).longValue());
        assertEquals("employee1", String.valueOf(row.get("username")));
        assertEquals("shadow_ai", String.valueOf(row.get("source_module")));
        assertEquals(String.valueOf(reportId), String.valueOf(row.get("source_event_id")));

        String adminToken = loginAndGetToken("admin", "admin");
        JsonNode adminList = getJson("/api/alert-center/list?page=1&pageSize=50", adminToken, status().isOk());
        assertTrue(containsSourceEvent(adminList.path("data").path("list"), String.valueOf(reportId)));

        String employeeToken = loginAndGetToken("employee1", "Passw0rd!");
        JsonNode employeeList = getJson("/api/alert-center/list?page=1&pageSize=50", employeeToken, status().isOk());
        assertTrue(containsSourceEvent(employeeList.path("data").path("list"), String.valueOf(reportId)));
    }

    @Test
    void securityReportShouldReturnAndPersistGovernanceLinkage() throws Exception {
        JsonNode reportResp = postPublicJson(
            "/api/security/events/report",
            Map.of(
                "eventType", "FILE_STEAL",
                "employeeId", "employee1",
                "hostname", "HOST-CONSISTENCY-EMPLOYEE1",
                "filePath", "C:/tmp/customer.csv",
                "targetAddr", "https://x.example/upload",
                "severity", "high"
            ),
            status().isOk()
        );
        assertEquals(20000, reportResp.path("code").asInt());

        long securityEventId = reportResp.path("data").path("id").asLong();
        long governanceEventId = reportResp.path("data").path("governanceEventId").asLong();
        long subjectUserId = reportResp.path("data").path("subjectUserId").asLong();
        assertTrue(securityEventId > 0);
        assertTrue(governanceEventId > 0);
        assertTrue(subjectUserId > 0);

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT user_id, username, source_module, source_event_id FROM governance_event WHERE id = ?",
            governanceEventId
        );
        assertEquals(subjectUserId, ((Number) row.get("user_id")).longValue());
        assertEquals("employee1", String.valueOf(row.get("username")));
        assertEquals("security", String.valueOf(row.get("source_module")));
        assertEquals(String.valueOf(securityEventId), String.valueOf(row.get("source_event_id")));

        String employeeToken = loginAndGetToken("employee1", "Passw0rd!");
        JsonNode employeeList = getJson("/api/alert-center/list?page=1&pageSize=50", employeeToken, status().isOk());
        assertTrue(containsSourceEvent(employeeList.path("data").path("list"), String.valueOf(securityEventId)));
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
        return token;
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

    private JsonNode postPublicJson(String path,
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
