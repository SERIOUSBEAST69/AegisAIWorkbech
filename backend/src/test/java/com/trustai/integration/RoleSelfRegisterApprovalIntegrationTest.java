package com.trustai.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class RoleSelfRegisterApprovalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean(name = "securitySchemaInitializer")
    private CommandLineRunner securitySchemaInitializerRunner;

    @MockBean(name = "privacyShieldSchemaInitializer")
    private CommandLineRunner privacyShieldSchemaInitializerRunner;

    @MockBean(name = "awardSchemaInitializer")
    private CommandLineRunner awardSchemaInitializerRunner;

    @Test
    void highRiskRoleOpenRegisterNeedsApproval() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin");
        String reviewerToken = loginAndGetToken("admin_reviewer", "admin");

        JsonNode rolesResp = getJson("/api/roles?page=1&pageSize=50", adminToken, status().isOk());
        assertEquals(20000, rolesResp.path("code").asInt());
        long secopsRoleId = findRoleIdByCode(rolesResp.path("data").path("list"), "SECOPS");
        assertTrue(secopsRoleId > 0L);

        JsonNode updateResp = putJson(
            "/api/roles/" + secopsRoleId,
            adminToken,
            Map.of(
                "name", "安全运维",
                "code", "SECOPS",
                "allowSelfRegister", true,
                "permissionCodes", java.util.List.of(),
                "reviewNote", "open_for_drill"
            ),
            status().isOk()
        );
        assertEquals(20000, updateResp.path("code").asInt());
        assertTrue(updateResp.path("data").path("pendingApproval").asBoolean());

        JsonNode publicBefore = getJson("/api/public/roles?companyId=1", null, status().isOk());
        assertEquals(20000, publicBefore.path("code").asInt());
        assertFalse(hasRoleCode(publicBefore.path("data"), "SECOPS"));

        JsonNode pendingResp = getJson(
            "/api/roles/self-register-requests?page=1&pageSize=20&status=pending",
            reviewerToken,
            status().isOk()
        );
        assertEquals(20000, pendingResp.path("code").asInt());
        long requestId = pendingResp.path("data").path("list").get(0).path("id").asLong(0L);
        assertTrue(requestId > 0L);

        JsonNode approveResp = putJson(
            "/api/roles/self-register-requests/" + requestId + "/approve",
            reviewerToken,
            Map.of("reviewNote", "approved"),
            status().isOk()
        );
        assertEquals(20000, approveResp.path("code").asInt());

        JsonNode publicAfter = getJson("/api/public/roles?companyId=1", null, status().isOk());
        assertEquals(20000, publicAfter.path("code").asInt());
        assertTrue(hasRoleCode(publicAfter.path("data"), "SECOPS"));
    }

    private boolean hasRoleCode(JsonNode rows, String code) {
        if (rows == null || !rows.isArray()) {
            return false;
        }
        for (JsonNode row : rows) {
            if (code.equalsIgnoreCase(row.path("code").asText())) {
                return true;
            }
        }
        return false;
    }

    private long findRoleIdByCode(JsonNode rows, String code) {
        if (rows == null || !rows.isArray()) {
            return 0L;
        }
        for (JsonNode row : rows) {
            if (code.equalsIgnoreCase(row.path("code").asText())) {
                return row.path("id").asLong(0L);
            }
        }
        return 0L;
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

    private JsonNode putJson(String path, String token, Object body, org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        var builder = put(path)
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
