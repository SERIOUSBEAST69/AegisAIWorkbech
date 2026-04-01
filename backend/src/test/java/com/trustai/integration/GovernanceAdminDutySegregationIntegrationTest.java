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
class GovernanceAdminDutySegregationIntegrationTest {

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
    void reviewerCanApproveButCannotCreateOrDeleteUser() throws Exception {
        ensureAdminPermission("user:manage");
        String reviewerToken = loginAndGetToken("admin_reviewer", "admin");

        JsonNode createResp = postJson(
            "/api/user/register",
            reviewerToken,
            Map.of(
                "username", "reviewer_forbidden_" + (System.currentTimeMillis() % 100000),
                "password", "Passw0rd!",
                "realName", "治理复核受限账号",
                "roleId", resolveEmployeeRoleId(),
                "department", "治理中心"
            ),
            status().isOk()
        );
        assertEquals(40300, createResp.path("code").asInt(), createResp.toString());

        Long pendingUserId = insertPendingUser("pending_for_reviewer_" + (System.currentTimeMillis() % 100000));
        JsonNode approveResp = postJson("/api/user/approve", reviewerToken, Map.of("id", pendingUserId), status().isOk());
        assertEquals(20000, approveResp.path("code").asInt(), approveResp.toString());

        JsonNode deleteResp = postJson(
            "/api/user/delete",
            reviewerToken,
            Map.of("id", pendingUserId, "confirmPassword", "admin", "deleteReason", "reviewer_forbidden"),
            status().isOk()
        );
        assertEquals(40300, deleteResp.path("code").asInt(), deleteResp.toString());
    }

    @Test
    void opsCanCreateAndDeleteButCannotApprove() throws Exception {
        ensureAdminPermission("user:manage");
        String opsToken = loginAndGetToken("admin_ops", "admin");
        String username = "ops_allowed_" + (System.currentTimeMillis() % 100000);

        JsonNode createResp = postJson(
            "/api/user/register",
            opsToken,
            Map.of(
                "username", username,
                "password", "Passw0rd!",
                "realName", "治理运营创建账号",
                "roleId", resolveEmployeeRoleId(),
                "department", "治理中心"
            ),
            status().isOk()
        );
        assertEquals(20000, createResp.path("code").asInt(), createResp.toString());

        Long createdUserId = jdbcTemplate.query(
            "SELECT id FROM sys_user WHERE username = ? ORDER BY id DESC LIMIT 1",
            ps -> ps.setString(1, username),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        assertTrue(createdUserId != null && createdUserId > 0L);

        Long pendingUserId = insertPendingUser("pending_for_ops_" + (System.currentTimeMillis() % 100000));
        JsonNode approveResp = postJson("/api/user/approve", opsToken, Map.of("id", pendingUserId), status().isOk());
        assertEquals(40300, approveResp.path("code").asInt(), approveResp.toString());

        JsonNode deleteResp = postJson(
            "/api/user/delete",
            opsToken,
            Map.of("id", createdUserId, "confirmPassword", "admin", "deleteReason", "ops_cleanup"),
            status().isOk()
        );
        assertEquals(20000, deleteResp.path("code").asInt(), deleteResp.toString());
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

    private Long resolveEmployeeRoleId() {
        return jdbcTemplate.query(
            "SELECT id FROM role WHERE company_id = 1 AND code = 'EMPLOYEE' ORDER BY id ASC LIMIT 1",
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private Long insertPendingUser(String username) {
        Long roleId = resolveEmployeeRoleId();
        Date now = new Date();
        jdbcTemplate.update(
            "INSERT INTO sys_user(company_id, username, password, real_name, nickname, role_id, department, organization_type, login_type, status, account_type, account_status, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            1L,
            username,
            "Passw0rd!",
            "待审批账号",
            username,
            roleId,
            "治理中心",
            "enterprise",
            "password",
            1,
            "real",
            "pending",
            now,
            now
        );
        return jdbcTemplate.query(
            "SELECT id FROM sys_user WHERE username = ? ORDER BY id DESC LIMIT 1",
            ps -> ps.setString(1, username),
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private void ensureAdminPermission(String permissionCode) {
        Long adminRoleId = jdbcTemplate.query(
            "SELECT id FROM role WHERE company_id = 1 AND code = 'ADMIN' ORDER BY id ASC LIMIT 1",
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (adminRoleId == null) {
            throw new IllegalStateException("ADMIN role not found");
        }
        Long permissionId = jdbcTemplate.query(
            "SELECT id FROM permission WHERE company_id = 1 AND code = ? ORDER BY id ASC LIMIT 1",
            ps -> ps.setString(1, permissionCode),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (permissionId == null) {
            Date now = new Date();
            jdbcTemplate.update(
                "INSERT INTO permission(company_id, name, code, type, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?)",
                1L,
                permissionCode,
                permissionCode,
                "button",
                now,
                now
            );
            permissionId = jdbcTemplate.query(
                "SELECT id FROM permission WHERE company_id = 1 AND code = ? ORDER BY id DESC LIMIT 1",
                ps -> ps.setString(1, permissionCode),
                rs -> rs.next() ? rs.getLong(1) : null
            );
        }
        final Long boundPermissionId = permissionId;
        Integer exists = jdbcTemplate.query(
            "SELECT COUNT(1) FROM role_permission WHERE role_id = ? AND permission_id = ?",
            ps -> {
                ps.setLong(1, adminRoleId);
                ps.setLong(2, boundPermissionId);
            },
            rs -> rs.next() ? rs.getInt(1) : 0
        );
        if (exists == null || exists == 0) {
            jdbcTemplate.update("INSERT INTO role_permission(role_id, permission_id) VALUES(?, ?)", adminRoleId, boundPermissionId);
        }
    }
}
