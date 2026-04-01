package com.trustai.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class PermissionMatrixIntegrationTest {

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
    void adminCanAccessUserListButSecopsCannot() throws Exception {
        ensurePermissionBindingForUser("admin", "user:manage");

        String adminToken = loginAndGetToken("admin", "admin");
        JsonNode adminResp = getJson("/api/user/list", adminToken, status().isOk());
        assertEquals(20000, adminResp.path("code").asInt());

        String secopsToken = loginAndGetToken("secops", "Passw0rd!");
        JsonNode secopsResp = getJson("/api/user/list", secopsToken, status().isForbidden());
        assertEquals(40300, secopsResp.path("code").asInt());
    }

    @Test
    void secopsCanAccessRiskEventsButEmployeeCannot() throws Exception {
        ensurePermissionBindingForUser("secops", "risk:event:view");

        String secopsToken = loginAndGetToken("secops", "Passw0rd!");
        JsonNode secopsResp = getJson("/api/risk-event/list", secopsToken, status().isOk());
        assertEquals(20000, secopsResp.path("code").asInt());

        String employeeToken = loginEmployeeToken();
        JsonNode employeeResp = getJson("/api/risk-event/list", employeeToken, status().isForbidden());
        assertEquals(40300, employeeResp.path("code").asInt());
    }

    @Test
    void employeeCanListOwnApprovalsButCannotAccessOperatorTodo() throws Exception {
        String employeeToken = loginEmployeeToken();
        JsonNode ownListResp = getJson("/api/approval/list", employeeToken, status().isOk());
        assertEquals(20000, ownListResp.path("code").asInt());

        JsonNode todoResp = getJson("/api/approval/todo", employeeToken, status().isForbidden());
        assertEquals(40300, todoResp.path("code").asInt());
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

    private String loginEmployeeToken() throws Exception {
        try {
            return loginAndGetToken("employee1", "Passw0rd!");
        } catch (AssertionError ignored) {
            return loginAndGetToken("employee", "Passw0rd!");
        }
    }

    private JsonNode postJson(String path, String token, Object body, org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
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

    private JsonNode getJson(String path, String token, org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        var builder = get(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(matcher)
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void ensurePermissionBindingForUser(String username, String permissionCode) {
        Long roleId = jdbcTemplate.query(
            "SELECT role_id FROM sys_user WHERE LOWER(username) = LOWER(?) ORDER BY id ASC LIMIT 1",
            ps -> ps.setString(1, username),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (roleId == null) {
            throw new IllegalStateException("Role not found for test user: " + username);
        }

        Long permissionId = jdbcTemplate.query(
            "SELECT id FROM permission WHERE code = ? ORDER BY id ASC LIMIT 1",
            ps -> ps.setString(1, permissionCode),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (permissionId == null) {
            Date now = new Date();
            jdbcTemplate.update(
                "INSERT INTO permission(company_id, name, code, type, parent_id, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?)",
                1L,
                permissionCode,
                permissionCode,
                "button",
                null,
                now,
                now
            );
            permissionId = jdbcTemplate.query(
                "SELECT id FROM permission WHERE code = ? ORDER BY id DESC LIMIT 1",
                ps -> ps.setString(1, permissionCode),
                rs -> rs.next() ? rs.getLong(1) : null
            );
        }

        if (permissionId == null) {
            throw new IllegalStateException("Permission not found for test code: " + permissionCode);
        }
        final Long boundPermissionId = permissionId;

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
