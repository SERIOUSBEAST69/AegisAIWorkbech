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
class GovernanceAdminAttackResilienceIntegrationTest {

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
    void employeeCannotReadGovernanceChangeQueue() throws Exception {
        String employeeToken = loginAndGetToken("employee", "Passw0rd!");
        JsonNode resp = getJson("/api/governance-change/page?page=1&pageSize=10", employeeToken, status().isForbidden());
        assertEquals(40300, resp.path("code").asInt(), resp.toString());
    }

    @Test
    void governanceChangeRequiresDualReviewAndSodBlocksAdminAdminReview() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin");
        String roleCode = "RISK_REVIEW_" + System.currentTimeMillis();
        String payload = objectMapper.writeValueAsString(Map.of(
            "name", "风险复核角色",
            "code", roleCode,
            "description", "集成测试创建"
        ));

        JsonNode submitResp = postJson(
            "/api/governance-change/submit",
            adminToken,
            Map.of("module", "ROLE", "action", "ADD", "payloadJson", payload, "confirmPassword", "admin"),
            status().isOk()
        );
        assertEquals(20000, submitResp.path("code").asInt(), submitResp.toString());
        long requestId = submitResp.path("data").path("id").asLong();
        assertTrue(requestId > 0L);

        JsonNode adminApprove = postJson(
            "/api/governance-change/approve",
            adminToken,
            Map.of("requestId", requestId, "approve", true, "confirmPassword", "admin", "note", "admin self review"),
            status().isOk()
        );
        assertEquals(40000, adminApprove.path("code").asInt(), adminApprove.toString());

        String secopsToken = loginAndGetToken("secops", "Passw0rd!");
        JsonNode secopsApprove = postJson(
            "/api/governance-change/approve",
            secopsToken,
            Map.of("requestId", requestId, "approve", true, "confirmPassword", "Passw0rd!", "note", "cross role review"),
            status().isOk()
        );
        assertEquals(20000, secopsApprove.path("code").asInt(), secopsApprove.toString());
        assertEquals("approved", secopsApprove.path("data").path("status").asText());
    }

    @Test
    void frequentSensitiveDeleteRequestsShouldTriggerFuse() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin");
        boolean hitFuse = false;
        for (int i = 0; i < 30; i++) {
            JsonNode resp = postJson(
                "/api/user/delete",
                adminToken,
                Map.of("id", -1, "confirmPassword", "admin", "deleteReason", "attack_test"),
                status().isOk()
            );
            if (resp.path("code").asInt() == 40000) {
                hitFuse = true;
                break;
            }
        }
        assertTrue(hitFuse, "expected sensitive operation fuse to trigger");
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
