package com.trustai.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import java.util.Map;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecOpsDutySegregationIntegrationTest {

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
    void secops2CanIgnoreButCannotBlockOrManageRules() throws Exception {
        Long eventId = insertSecurityEvent("employee1", "pending");
        String token = loginAndGetToken("secops_2", "Passw0rd!");

        JsonNode ignoreResp = postJson("/api/security/ignore", token, Map.of("id", eventId), status().isOk());
        assertEquals(20000, ignoreResp.path("code").asInt(), ignoreResp.toString());

        Long eventId2 = insertSecurityEvent("employee1", "pending");
        JsonNode blockResp = postJson("/api/security/block", token, Map.of("id", eventId2), status().isOk());
        assertEquals(40300, blockResp.path("code").asInt(), blockResp.toString());

        JsonNode ruleResp = postJson(
            "/api/security/rules",
            token,
            Map.of(
                "name", "secops2_rule_forbidden",
                "sensitiveExtensions", ".pdf,.docx",
                "sensitivePaths", "C:/Users",
                "alertThresholdBytes", 4096,
                "enabled", true,
                "description", "forbidden"
            ),
            status().isOk()
        );
        assertEquals(40300, ruleResp.path("code").asInt(), ruleResp.toString());
    }

    @Test
    void secops3CanBlockButCannotIgnoreOrManageRules() throws Exception {
        Long eventId = insertSecurityEvent("employee1", "pending");
        String token = loginAndGetToken("secops_3", "Passw0rd!");

        JsonNode blockResp = postJson("/api/security/block", token, Map.of("id", eventId), status().isOk());
        assertEquals(20000, blockResp.path("code").asInt(), blockResp.toString());

        Long eventId2 = insertSecurityEvent("employee1", "pending");
        JsonNode ignoreResp = postJson("/api/security/ignore", token, Map.of("id", eventId2), status().isOk());
        assertEquals(40300, ignoreResp.path("code").asInt(), ignoreResp.toString());

        JsonNode ruleResp = postJson(
            "/api/security/rules",
            token,
            Map.of(
                "name", "secops3_rule_forbidden",
                "sensitiveExtensions", ".csv",
                "sensitivePaths", "C:/Desktop",
                "alertThresholdBytes", 4096,
                "enabled", true,
                "description", "forbidden"
            ),
            status().isOk()
        );
        assertEquals(40300, ruleResp.path("code").asInt(), ruleResp.toString());
    }

    @Test
    void secopsCommanderCanBlockIgnoreAndManageRules() throws Exception {
        String token = loginAndGetToken("secops", "Passw0rd!");
        Long eventId = insertSecurityEvent("employee1", "pending");

        JsonNode blockResp = postJson("/api/security/block", token, Map.of("id", eventId), status().isOk());
        assertEquals(20000, blockResp.path("code").asInt(), blockResp.toString());

        Long eventId2 = insertSecurityEvent("employee1", "pending");
        JsonNode ignoreResp = postJson("/api/security/ignore", token, Map.of("id", eventId2), status().isOk());
        assertEquals(20000, ignoreResp.path("code").asInt(), ignoreResp.toString());

        JsonNode ruleResp = postJson(
            "/api/security/rules",
            token,
            Map.of(
                "name", "secops_commander_rule_" + (System.currentTimeMillis() % 100000),
                "sensitiveExtensions", ".sql,.xlsx",
                "sensitivePaths", "C:/Users,/Data",
                "alertThresholdBytes", 8192,
                "enabled", true,
                "description", "allowed"
            ),
            status().isOk()
        );
        assertEquals(20000, ruleResp.path("code").asInt(), ruleResp.toString());
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        JsonNode loginResp = postJson(
            "/api/auth/login",
            null,
            Map.of("username", username, "password", password),
            status().isOk()
        );
        assertEquals(20000, loginResp.path("code").asInt(), loginResp.toString());
        String token = loginResp.path("data").path("token").asText();
        assertTrue(token != null && !token.isBlank());
        return token;
    }

    private Long insertSecurityEvent(String employeeId, String statusValue) {
        Date now = new Date();
        jdbcTemplate.update(
            "INSERT INTO security_event(company_id, event_type, file_path, target_addr, employee_id, hostname, file_size, severity, status, source, policy_version, event_time, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            1L,
            "FILE_STEAL",
            "C:/Users/demo/Documents/customer.xlsx",
            "https://example-malicious.com/upload",
            employeeId,
            "HOST-SECOPS",
            20480L,
            "high",
            statusValue,
            "agent",
            1L,
            now,
            now,
            now
        );
        return jdbcTemplate.query(
            "SELECT id FROM security_event WHERE company_id = 1 AND employee_id = ? ORDER BY id DESC LIMIT 1",
            ps -> ps.setString(1, employeeId),
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private JsonNode postJson(String path, String token, Object body, org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        var builder = post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
            builder.header("X-Company-Id", "1");
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(matcher)
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
