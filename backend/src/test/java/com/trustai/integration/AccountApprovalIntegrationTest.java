package com.trustai.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trustai.entity.User;
import com.trustai.repository.AssetEsRepository;
import com.trustai.repository.AuditLogEsRepository;
import com.trustai.repository.ModelEsRepository;
import com.trustai.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountApprovalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean(name = "securitySchemaInitializer")
    private CommandLineRunner securitySchemaInitializerRunner;

    @MockBean(name = "privacyShieldSchemaInitializer")
    private CommandLineRunner privacyShieldSchemaInitializerRunner;

    @MockBean(name = "awardSchemaInitializer")
    private CommandLineRunner awardSchemaInitializerRunner;

    @MockBean
    private AssetEsRepository assetEsRepository;

    @MockBean
    private ModelEsRepository modelEsRepository;

    @MockBean
    private AuditLogEsRepository auditLogEsRepository;

    @Test
    void realAccountMustBeApprovedOrRejectedBeforeLogin() throws Exception {
        String approvableUsername = uniqueUsername("approve");
        String rejectedUsername = uniqueUsername("reject");
        makeEmployeeRoleSelfRegisterable();

        JsonNode adminLoginResp = login("admin", "admin");
        assertEquals(20000, adminLoginResp.path("code").asInt());
        String adminToken = adminLoginResp.path("data").path("token").asText();
        String adminCompanyName = resolveCompanyNameByUsername("admin");
        assertTrue(!adminToken.isBlank());
        assertTrue(!adminCompanyName.isBlank());
        ensureCompanyEmployeeRoleSelfRegister(adminCompanyName);

        JsonNode registerPendingResp = registerRealAccount(approvableUsername, adminCompanyName);
        assertEquals(20000, registerPendingResp.path("code").asInt(), registerPendingResp.toString());
        assertFalse(registerPendingResp.path("data").path("authenticated").asBoolean());
        assertTrue(registerPendingResp.path("data").path("pendingApproval").asBoolean());
        assertEquals("pending", registerPendingResp.path("data").path("accountStatus").asText());
        long pendingCompanyId = registerPendingResp.path("data").path("user").path("companyId").asLong(0L);
        assertTrue(pendingCompanyId > 0L);
        alignAdminCompany(pendingCompanyId);

        JsonNode pendingLoginResp = login(approvableUsername, "Passw0rd!");
        assertEquals(40100, pendingLoginResp.path("code").asInt());

        long pendingUserId = registerPendingResp.path("data").path("user").path("id").asLong(0L);
        assertTrue(pendingUserId > 0L);

        activateUser(pendingUserId);

        JsonNode activeLoginResp = login(approvableUsername, "Passw0rd!");
        assertEquals(20000, activeLoginResp.path("code").asInt());
        assertEquals("active", activeLoginResp.path("data").path("user").path("accountStatus").asText());

        String tenantCompanyName = resolveCompanyNameById(pendingCompanyId);
        ensureCompanyEmployeeRoleSelfRegister(tenantCompanyName);
        JsonNode registerRejectedResp = registerRealAccount(rejectedUsername, tenantCompanyName);
        assertEquals(20000, registerRejectedResp.path("code").asInt());
        assertTrue(registerRejectedResp.path("data").path("pendingApproval").asBoolean());
        long rejectedCompanyId = registerRejectedResp.path("data").path("user").path("companyId").asLong(0L);
        assertTrue(rejectedCompanyId > 0L);
        alignAdminCompany(rejectedCompanyId);

        long rejectUserId = registerRejectedResp.path("data").path("user").path("id").asLong(0L);
        assertTrue(rejectUserId > 0L);

        rejectUser(rejectUserId, "incomplete_docs");

        JsonNode rejectedLoginResp = login(rejectedUsername, "Passw0rd!");
        assertEquals(40100, rejectedLoginResp.path("code").asInt());
        assertTrue(rejectedLoginResp.path("msg").asText().contains("incomplete_docs"));
    }

    private JsonNode registerRealAccount(String username, String companyName) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("username", username);
        req.put("password", "Passw0rd!");
        req.put("confirmPassword", "Passw0rd!");
        req.put("realName", "审批测试用户");
        req.put("nickname", "审批测试");
        req.put("roleCode", "EMPLOYEE");
        req.put("organizationType", "enterprise");
        req.put("department", "测试部");
        req.put("phone", buildTestPhone(username));
        req.put("email", username + "@test.local");
        req.put("loginType", "password");
        req.put("accountType", "real");
        req.put("companyName", companyName);
        return postJson("/api/auth/register", null, req);
    }

    private JsonNode login(String username, String password) throws Exception {
        return postJson("/api/auth/login", null, Map.of("username", username, "password", password));
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

    private void alignAdminCompany(long companyId) {
        User admin = userService.lambdaQuery().eq(User::getUsername, "admin").one();
        assertTrue(admin != null);
        admin.setCompanyId(companyId);
        userService.updateById(admin);
    }

    private void activateUser(long userId) {
        User target = userService.getById(userId);
        assertTrue(target != null);
        User admin = userService.lambdaQuery().eq(User::getUsername, "admin").one();
        assertTrue(admin != null);
        target.setAccountStatus("active");
        target.setRejectReason(null);
        target.setApprovedBy(admin.getId());
        target.setApprovedAt(new java.util.Date());
        target.setStatus(1);
        target.setUpdateTime(new java.util.Date());
        userService.updateById(target);
    }

    private void rejectUser(long userId, String reason) {
        User target = userService.getById(userId);
        assertTrue(target != null);
        User admin = userService.lambdaQuery().eq(User::getUsername, "admin").one();
        assertTrue(admin != null);
        target.setAccountStatus("rejected");
        target.setRejectReason(reason);
        target.setApprovedBy(admin.getId());
        target.setApprovedAt(new java.util.Date());
        target.setUpdateTime(new java.util.Date());
        userService.updateById(target);
    }

    private String uniqueUsername(String prefix) {
        return prefix + "_" + System.currentTimeMillis();
    }

    private String buildTestPhone(String seed) {
        int value = Math.floorMod(seed.hashCode(), 100_000_000);
        return "139" + String.format("%08d", value);
    }

    private void makeEmployeeRoleSelfRegisterable() {
        jdbcTemplate.update("UPDATE role SET allow_self_register = TRUE WHERE code = 'EMPLOYEE'");
    }

    private void ensureCompanyEmployeeRoleSelfRegister(String companyName) {
        Long companyId = jdbcTemplate.query(
            "SELECT id FROM company WHERE company_name = ? ORDER BY id ASC LIMIT 1",
            ps -> ps.setString(1, companyName),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (companyId == null) {
            User admin = userService.lambdaQuery().eq(User::getUsername, "admin").one();
            companyId = admin == null ? null : admin.getCompanyId();
        }
        assertTrue(companyId != null && companyId > 0L);
        long resolvedCompanyId = companyId;

        Long roleId = jdbcTemplate.query(
            "SELECT id FROM role WHERE company_id = ? AND code = 'EMPLOYEE' ORDER BY id ASC LIMIT 1",
            ps -> ps.setLong(1, resolvedCompanyId),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (roleId == null) {
            jdbcTemplate.update(
                "INSERT INTO role(company_id, name, code, description, allow_self_register, is_system, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                resolvedCompanyId,
                "普通员工",
                "EMPLOYEE",
                "审批测试自动创建",
                true,
                false
            );
            roleId = jdbcTemplate.query(
                "SELECT id FROM role WHERE company_id = ? AND code = 'EMPLOYEE' ORDER BY id DESC LIMIT 1",
                ps -> ps.setLong(1, resolvedCompanyId),
                rs -> rs.next() ? rs.getLong(1) : null
            );
        }
        assertTrue(roleId != null && roleId > 0L);
        jdbcTemplate.update("UPDATE role SET allow_self_register = TRUE WHERE id = ?", roleId);
    }

    private String resolveCompanyNameByUsername(String username) {
        return jdbcTemplate.query(
            "SELECT c.company_name FROM sys_user u JOIN company c ON c.id = u.company_id WHERE LOWER(u.username) = LOWER(?) ORDER BY u.id ASC LIMIT 1",
            ps -> ps.setString(1, username),
            rs -> rs.next() ? rs.getString(1) : ""
        );
    }

    private String resolveCompanyNameById(long companyId) {
        return jdbcTemplate.query(
            "SELECT company_name FROM company WHERE id = ? LIMIT 1",
            ps -> ps.setLong(1, companyId),
            rs -> rs.next() ? rs.getString(1) : ""
        );
    }

}
