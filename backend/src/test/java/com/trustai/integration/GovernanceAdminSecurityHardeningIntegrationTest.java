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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GovernanceAdminSecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean(name = "securitySchemaInitializer")
    private CommandLineRunner securitySchemaInitializerRunner;

    @MockBean(name = "privacyShieldSchemaInitializer")
    private CommandLineRunner privacyShieldSchemaInitializerRunner;

    @MockBean(name = "companySchemaInitializer")
    private CommandLineRunner companySchemaInitializerRunner;

    @MockBean(name = "awardSchemaInitializer")
    private CommandLineRunner awardSchemaInitializerRunner;

    @Test
    void deleteMustRequireSecondPasswordAndSupportRestore() throws Exception {
        String adminToken = loginAndGetToken("admin_reviewer", "admin");
        long roleId = resolveAnyRoleId(adminToken);
        String username = "gov_harden_" + System.currentTimeMillis();

        JsonNode createResp = postJson(
            "/api/user/register",
            adminToken,
            Map.of(
                "username", username,
                "password", "Passw0rd!",
                "realName", "治理加固测试用户",
                "roleId", roleId,
                "department", "治理中心"
            ),
            status().isOk()
        );
        assertEquals(20000, createResp.path("code").asInt(), createResp.toString());

        JsonNode pageResp = getJson("/api/user/page?page=1&pageSize=20&username=" + username, adminToken, status().isOk());
        long userId = pageResp.path("data").path("list").get(0).path("id").asLong();
        assertTrue(userId > 0L);

        JsonNode deleteWithoutConfirm = postJson(
            "/api/user/delete",
            adminToken,
            Map.of("id", userId),
            status().isOk()
        );
        assertEquals(40000, deleteWithoutConfirm.path("code").asInt(), deleteWithoutConfirm.toString());

        JsonNode deleteResp = postJson(
            "/api/user/delete",
            adminToken,
            Map.of("id", userId, "confirmPassword", "admin", "deleteReason", "integration_test"),
            status().isOk()
        );
        assertEquals(20000, deleteResp.path("code").asInt(), deleteResp.toString());

        JsonNode recyclePage = getJson("/api/user/recycle-bin/page?page=1&pageSize=10&username=" + username, adminToken, status().isOk());
        assertEquals(20000, recyclePage.path("code").asInt(), recyclePage.toString());
        long recycleId = recyclePage.path("data").path("list").get(0).path("id").asLong();
        assertTrue(recycleId > 0L);

        JsonNode restoreResp = postJson(
            "/api/user/recycle-bin/restore",
            adminToken,
            Map.of("recycleId", recycleId, "confirmPassword", "admin"),
            status().isOk()
        );
        assertEquals(20000, restoreResp.path("code").asInt(), restoreResp.toString());

        JsonNode restoredLogin = postJson(
            "/api/auth/login",
            null,
            Map.of("username", username, "password", "Passw0rd!"),
            status().isOk()
        );
        assertEquals(20000, restoredLogin.path("code").asInt(), restoredLogin.toString());
    }

    private long resolveAnyRoleId(String token) throws Exception {
        JsonNode rolesResp = getJson("/api/role/list", token, status().isOk());
        JsonNode roles = rolesResp.path("data");
        assertTrue(roles.isArray() && roles.size() > 0, "roles should not be empty");
        return roles.get(0).path("id").asLong();
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
}