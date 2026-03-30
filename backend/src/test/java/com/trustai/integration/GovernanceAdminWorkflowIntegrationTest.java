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
class GovernanceAdminWorkflowIntegrationTest {

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
    void governanceAdminCoreMenusShouldLoadWithoutSystemBusy() throws Exception {
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
}
