package com.trustai.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AwardAndObservabilityIntegrationTest {

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

    @MockBean(name = "companySchemaInitializer")
    private CommandLineRunner companySchemaInitializerRunner;

    @Test
    void awardSummaryAndComplianceMappingShouldBeAvailableForAdmin() throws Exception {
        String token = loginAsAdmin();
        JsonNode summary = getJson("/api/award/summary", token);
        assertEquals(20000, summary.path("code").asInt(), summary.toString());
        assertTrue(summary.path("data").isObject());

        JsonNode mapping = getJson("/api/award/compliance-mapping", token);
        assertEquals(20000, mapping.path("code").asInt(), mapping.toString());
        assertTrue(mapping.path("data").path("PIPL").isArray());
    }

    @Test
    void webVitalsShouldPersistAndBeQueryable() throws Exception {
        ensurePermissionBindingForRole("ADMIN", "ops:metrics:view");
        JsonNode ingest = postJson("/api/ops-metrics/web-vitals", null, Map.of(
            "name", "LCP",
            "value", 1200,
            "rating", "good",
            "id", "integration-vital"
        ));
        assertEquals(20000, ingest.path("code").asInt(), ingest.toString());

        String token = loginAsAdmin();
        JsonNode summary = getJson("/api/ops-metrics/web-vitals/summary?days=7", token);
        assertEquals(20000, summary.path("code").asInt(), summary.toString());
        assertTrue(summary.path("data").path("summary").isArray());
    }

    @Test
    void httpHistoryEndpointShouldReturnPayload() throws Exception {
        ensurePermissionBindingForRole("ADMIN", "ops:metrics:view");
        String token = loginAsAdmin();
        JsonNode resp = getJson("/api/ops-metrics/http-history?days=7", token);
        assertEquals(20000, resp.path("code").asInt(), resp.toString());
        assertTrue(resp.path("data").path("rows").isArray());
    }

    private String loginAsAdmin() throws Exception {
        JsonNode loginResp = postJson("/api/auth/login", null, Map.of("username", "admin", "password", "admin"));
        assertEquals(20000, loginResp.path("code").asInt());
        String token = loginResp.path("data").path("token").asText();
        assertTrue(token != null && !token.isBlank());
        return token;
    }

    private JsonNode getJson(String path, String token) throws Exception {
        var builder = get(path);
        builder.header("X-Company-Id", "1");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        var builder = post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
        builder.header("X-Company-Id", "1");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void ensurePermissionBindingForRole(String roleCode, String permissionCode) {
        Long roleId = jdbcTemplate.query(
            "SELECT id FROM role WHERE company_id = 1 AND code = ? ORDER BY id ASC LIMIT 1",
            ps -> ps.setString(1, roleCode),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        assertTrue(roleId != null && roleId > 0L);

        Long permissionId = jdbcTemplate.query(
            "SELECT id FROM permission WHERE company_id = 1 AND code = ? ORDER BY id ASC LIMIT 1",
            ps -> ps.setString(1, permissionCode),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (permissionId == null) {
            jdbcTemplate.update(
                "INSERT INTO permission(company_id, name, code, type, create_time, update_time) VALUES(?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                1L,
                permissionCode,
                permissionCode,
                "button"
            );
            permissionId = jdbcTemplate.query(
                "SELECT id FROM permission WHERE company_id = 1 AND code = ? ORDER BY id DESC LIMIT 1",
                ps -> ps.setString(1, permissionCode),
                rs -> rs.next() ? rs.getLong(1) : null
            );
        }
        assertTrue(permissionId != null && permissionId > 0L);

        Long boundPermissionId = permissionId;
        Integer exists = jdbcTemplate.query(
            "SELECT COUNT(1) FROM role_permission WHERE role_id = ? AND permission_id = ?",
            ps -> {
                ps.setLong(1, roleId);
                ps.setLong(2, boundPermissionId);
            },
            rs -> rs.next() ? rs.getInt(1) : 0
        );
        if (exists == null || exists == 0) {
            jdbcTemplate.update("INSERT INTO role_permission(role_id, permission_id) VALUES(?, ?)", roleId, boundPermissionId);
        }
    }
}
