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
class GovernanceAdminWorkflowIntegrationTest {

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
    void governanceAdminCoreMenusShouldLoadWithoutSystemBusy() throws Exception {
        ensurePolicyTable();
        ensurePermissionBindingForRole("ADMIN", "user:manage");
        ensurePermissionBindingForRole("ADMIN", "role:manage");
        ensurePermissionBindingForRole("ADMIN", "permission:manage");
        ensurePermissionBindingForRole("ADMIN", "policy:view");
        String token = loginAndGetToken("admin", "admin");

        JsonNode userPage = getJson("/api/user/page?page=1&pageSize=10", token, status().isOk());
        assertEquals(20000, userPage.path("code").asInt());
        assertTrue(userPage.path("data").path("list").isArray());

        JsonNode rolePage = getJson("/api/role/page?page=1&pageSize=10", token, status().isOk());
        assertEquals(20000, rolePage.path("code").asInt());
        assertTrue(rolePage.path("data").path("list").isArray());

        JsonNode permissionPage = getJson("/api/permission/page?page=1&pageSize=10", token, status().isOk());
        assertEquals(20000, permissionPage.path("code").asInt());
        assertTrue(permissionPage.path("data").path("list").isArray());

        JsonNode approvalPage = getJson("/api/approval/page?page=1&pageSize=10", token, status().isOk());
        assertEquals(20000, approvalPage.path("code").asInt());
        assertTrue(approvalPage.path("data").path("list").isArray());

        JsonNode policyList = getJson("/api/policy/list", token, status().isOk());
        assertEquals(20000, policyList.path("code").asInt());
        assertTrue(policyList.path("data").isArray());

        JsonNode subjectList = getJson("/api/subject-request/list", token, status().isOk());
        assertEquals(20000, subjectList.path("code").asInt());
        assertTrue(subjectList.path("data").isArray());

        JsonNode duplicateResp = postJson(
            "/api/user/register",
            token,
            Map.of(
                "username", "admin",
                "password", "Passw0rd!",
                "realName", "重复管理员",
                "roleId", 1,
                "department", "治理中心"
            ),
            status().isOk()
        );
        assertEquals(40000, duplicateResp.path("code").asInt());
    }

    @Test
    void governanceAdminSubmitShouldNotFallbackToUnauthenticated() throws Exception {
        String token = loginAndGetToken("admin_ops", "admin");
        String payload = objectMapper.writeValueAsString(Map.of(
            "name", "治理变更回归角色",
            "code", "GOV_ADMIN_REG_" + System.currentTimeMillis(),
            "description", "回归测试"
        ));

        JsonNode submitResp = postJson(
            "/api/governance-change/submit",
            token,
            Map.of("module", "ROLE", "action", "ADD", "payloadJson", payload, "confirmPassword", "admin"),
            status().isOk()
        );

        assertEquals(20000, submitResp.path("code").asInt(), submitResp.toString());
        assertTrue(submitResp.path("data").path("id").asLong() > 0L, submitResp.toString());
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
        assertTrue(token != null && !token.isBlank());
        return token;
    }

    private JsonNode postJson(String path, String token, Object body, org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        var builder = post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
        builder.header("X-Company-Id", "1");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(matcher)
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path, String token, org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        var builder = get(path).contentType(MediaType.APPLICATION_JSON);
        builder.header("X-Company-Id", "1");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(matcher)
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
}
