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
class SecOpsPolicyWorkflowIntegrationTest {

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

    @MockBean(name = "awardSchemaInitializer")
    private CommandLineRunner awardSchemaInitializerRunner;

    @Test
    void secopsShouldManagePolicyWithSecondPasswordAndDataVisibleInList() throws Exception {
        ensurePolicyTable();
        ensurePermissionBindingForRole("SECOPS", "policy:structure:manage");
        ensurePermissionBindingForRole("SECOPS", "policy:status:toggle");
        ensurePermissionBindingForRole("SECOPS", "policy:view");
        String secopsToken = loginAndGetToken("secops", "Passw0rd!");
        String policyName = "SECOPS_POLICY_" + System.currentTimeMillis();

        JsonNode saveResp = postJson(
            "/api/policy/save",
            secopsToken,
            Map.of(
                "name", policyName,
                "ruleContent", "{\"keywords\":[\"身份证号\"]}",
                "scope", "ai_prompt",
                "status", "ACTIVE",
                "confirmPassword", "Passw0rd!"
            )
        );
        assertEquals(20000, saveResp.path("code").asInt(), saveResp.toString());

        JsonNode listResp = getJson("/api/policy/list?name=" + policyName, secopsToken);
        assertEquals(20000, listResp.path("code").asInt(), listResp.toString());
        JsonNode list = listResp.path("data");
        assertTrue(list.isArray(), listResp.toString());
        assertTrue(list.size() > 0, listResp.toString());
        long policyId = list.get(0).path("id").asLong();
        assertTrue(policyId > 0L, listResp.toString());

        JsonNode deleteResp = postJson(
            "/api/policy/delete",
            secopsToken,
            Map.of("id", policyId, "confirmPassword", "Passw0rd!")
        );
        assertEquals(20000, deleteResp.path("code").asInt(), deleteResp.toString());
    }

    private void ensurePolicyTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS compliance_policy (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              company_id BIGINT NOT NULL,
              name VARCHAR(128) NOT NULL,
              rule_content CLOB,
              scope VARCHAR(64),
              status INT DEFAULT 1,
              version INT DEFAULT 1,
              create_time TIMESTAMP,
              update_time TIMESTAMP
            )
            """);
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
        builder.header("X-Company-Id", "1");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path, String token) throws Exception {
        var builder = get(path).contentType(MediaType.APPLICATION_JSON);
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
