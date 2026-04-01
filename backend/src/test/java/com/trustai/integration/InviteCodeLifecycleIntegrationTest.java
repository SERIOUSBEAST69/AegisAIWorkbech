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
import com.trustai.repository.AssetEsRepository;
import com.trustai.repository.AuditLogEsRepository;
import com.trustai.repository.ModelEsRepository;
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
class InviteCodeLifecycleIntegrationTest {

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

    @MockBean
    private AssetEsRepository assetEsRepository;

    @MockBean
    private ModelEsRepository modelEsRepository;

    @MockBean
    private AuditLogEsRepository auditLogEsRepository;

    @Test
    void inviteCodeCanBeCreatedRevokedAndReactivated() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin");

        JsonNode createResp = postJson(
            "/api/auth/invite-code/create",
            adminToken,
            Map.of("expireHours", 24),
            status().isOk()
        );
        assertEquals(20000, createResp.path("code").asInt(), () -> "invite create response=" + createResp);
        String inviteCode = createResp.path("data").path("inviteCode").asText();
        assertTrue(inviteCode != null && !inviteCode.isBlank());

        JsonNode listResp = getJson("/api/auth/invite-code/list?page=1&pageSize=10", adminToken, status().isOk());
        assertEquals(20000, listResp.path("code").asInt(), () -> "invite list response=" + listResp);
        assertTrue(listResp.path("data").path("list").isArray());

        long inviteId = findInviteIdByCode(listResp.path("data").path("list"), inviteCode);
        assertTrue(inviteId > 0L);

        JsonNode revokeResp = putJson(
            "/api/auth/invite-code/" + inviteId + "/revoke",
            adminToken,
            Map.of("reason", "security_drill"),
            status().isOk()
        );
        assertEquals(20000, revokeResp.path("code").asInt());

        JsonNode optionsAfterRevoke = getJson(
            "/api/auth/registration-options?inviteCode=" + inviteCode,
            null,
            status().isOk()
        );
        assertEquals(40000, optionsAfterRevoke.path("code").asInt());

        JsonNode reactivateResp = putJson(
            "/api/auth/invite-code/" + inviteId + "/reactivate",
            adminToken,
            Map.of(),
            status().isOk()
        );
        assertEquals(20000, reactivateResp.path("code").asInt());

        JsonNode optionsAfterReactivate = getJson(
            "/api/auth/registration-options?inviteCode=" + inviteCode,
            null,
            status().isOk()
        );
        assertEquals(20000, optionsAfterReactivate.path("code").asInt());
        assertFalse(optionsAfterReactivate.path("data").path("identities").isEmpty());
    }

    private long findInviteIdByCode(JsonNode rows, String inviteCode) {
        for (JsonNode row : rows) {
            if (inviteCode.equals(row.path("inviteCode").asText())) {
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
