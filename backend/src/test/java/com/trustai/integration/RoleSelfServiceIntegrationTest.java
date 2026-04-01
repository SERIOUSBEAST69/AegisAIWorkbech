package com.trustai.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import java.util.List;
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
class RoleSelfServiceIntegrationTest {

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
    void adminCanCreateUpdateDeleteCustomRole() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin");
        String roleCode = "TEST_SELF_ROLE_" + (System.currentTimeMillis() % 100000);

        JsonNode createResp = invokeJson(
            post("/api/roles"),
            adminToken,
            Map.of(
                "name", "测试自定义角色",
                "code", roleCode,
                "allowSelfRegister", true,
                "permissionCodes", List.of("menu:data_asset")
            ),
            status().isOk()
        );
        Long roleId = createResp.path("data").path("id").asLong();
        assertTrue(roleId != null && roleId > 0);

        JsonNode updateResp = invokeJson(
            put("/api/roles/" + roleId),
            adminToken,
            Map.of(
                "name", "测试自定义角色-更新",
                "code", roleCode,
                "allowSelfRegister", false,
                "permissionCodes", List.of("menu:data_asset", "approval:view")
            ),
            status().isOk()
        );
        assertEquals(20000, updateResp.path("code").asInt());
        assertEquals(false, updateResp.path("data").path("allowSelfRegister").asBoolean());

        JsonNode deleteResp = invokeJson(
            delete("/api/roles/" + roleId),
            adminToken,
            null,
            status().isOk()
        );
        assertEquals(20000, deleteResp.path("code").asInt());
    }

    @Test
    void ordinaryUserWithoutRoleManageCannotAccessRoleApis() throws Exception {
        String username = "norole_" + (System.currentTimeMillis() % 100000);
        insertUser(username, null);
        String token = loginAndGetToken(username, "Passw0rd!");
        JsonNode me = invokeJson(get("/api/auth/me"), token, null, status().isOk());
        assertEquals(username, me.path("data").path("user").path("username").asText());
        JsonNode resp = invokeJson(get("/api/roles"), token, null, status().isOk());
        assertEquals(40300, resp.path("code").asInt());
    }

    @Test
    void publicRolesContainsSelfRegisterRoles() throws Exception {
        JsonNode resp = invokeJson(get("/api/public/roles?companyId=1"), null, null, status().isOk());
        assertEquals(20000, resp.path("code").asInt());
        JsonNode roles = resp.path("data");
        assertTrue(roles.isArray());
        boolean containsEmployee = false;
        for (JsonNode item : roles) {
            if ("EMPLOYEE".equalsIgnoreCase(item.path("code").asText())) {
                containsEmployee = true;
                break;
            }
        }
        assertTrue(containsEmployee);
    }

    @Test
    void customRolePermissionControlsProtectedApiAccess() throws Exception {
        String roleCode = "PERM_TEST_" + (System.currentTimeMillis() % 100000);
        String username = "permuser_" + (System.currentTimeMillis() % 100000);
        Long roleId = insertRole(roleCode, "权限测试角色", false);
        insertUser(username, roleId);

        jdbcTemplate.update("DELETE FROM role_permission WHERE role_id = ?", roleId);

        String tokenBeforeBind = loginAndGetToken(username, "Passw0rd!");
        JsonNode beforeMe = invokeJson(get("/api/auth/me"), tokenBeforeBind, null, status().isOk());
        assertEquals(username, beforeMe.path("data").path("user").path("username").asText());
        JsonNode forbiddenResp = invokeJson(get("/api/roles"), tokenBeforeBind, null, status().isOk());
        assertEquals(40300, forbiddenResp.path("code").asInt());

        Long permissionId = ensurePermission("role:manage", "角色管理", "button");
        ensureRolePermission(roleId, permissionId);

        String tokenAfterBind = loginAndGetToken(username, "Passw0rd!");
        JsonNode allowResp = invokeJson(get("/api/roles"), tokenAfterBind, null, status().isOk());
        assertEquals(20000, allowResp.path("code").asInt());
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        JsonNode loginResp = invokeJson(
            post("/api/auth/login"),
            null,
            Map.of("username", username, "password", password),
            status().isOk()
        );
        assertEquals(20000, loginResp.path("code").asInt());
        String token = loginResp.path("data").path("token").asText();
        assertNotNull(token);
        assertTrue(!token.isBlank());
        return token;
    }

    private JsonNode invokeJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                                String token,
                                Object body,
                                org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
            builder.header("X-Company-Id", "1");
        }
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(matcher)
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Long insertRole(String code, String name, boolean allowSelfRegister) {
        Date now = new Date();
        jdbcTemplate.update(
            "INSERT INTO role(company_id, name, code, description, allow_self_register, is_system, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
            1L,
            name,
            code,
            "integration_test",
            allowSelfRegister,
            false,
            now,
            now
        );
        return jdbcTemplate.query(
            "SELECT id FROM role WHERE company_id = 1 AND code = ? ORDER BY id DESC LIMIT 1",
            ps -> ps.setString(1, code),
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private void insertUser(String username, Long roleId) {
        Date now = new Date();
        jdbcTemplate.update(
            "INSERT INTO sys_user(company_id, username, password, real_name, nickname, role_id, department, organization_type, login_type, status, account_type, account_status, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            1L,
            username,
            "Passw0rd!",
            "权限测试用户",
            username,
            roleId,
            "测试部",
            "enterprise",
            "password",
            1,
            "real",
            "active",
            now,
            now
        );
    }

    private Long ensurePermission(String code, String name, String type) {
        Long existingId = jdbcTemplate.query(
            "SELECT id FROM permission WHERE company_id = 1 AND code = ? ORDER BY id ASC LIMIT 1",
            ps -> ps.setString(1, code),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (existingId != null) {
            return existingId;
        }

        Date now = new Date();
        jdbcTemplate.update(
            "INSERT INTO permission(company_id, name, code, type, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?)",
            1L,
            name,
            code,
            type,
            now,
            now
        );

        return jdbcTemplate.query(
            "SELECT id FROM permission WHERE company_id = 1 AND code = ? ORDER BY id DESC LIMIT 1",
            ps -> ps.setString(1, code),
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private void ensureRolePermission(Long roleId, Long permissionId) {
        Integer exists = jdbcTemplate.query(
            "SELECT COUNT(1) FROM role_permission WHERE role_id = ? AND permission_id = ?",
            ps -> {
                ps.setLong(1, roleId);
                ps.setLong(2, permissionId);
            },
            rs -> rs.next() ? rs.getInt(1) : 0
        );
        if (exists == null || exists == 0) {
            jdbcTemplate.update("INSERT INTO role_permission(role_id, permission_id) VALUES(?, ?)", roleId, permissionId);
        }
    }
}
