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
class ExecutivePersonaIntegrationTest {

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
    void executiveGetsSummaryOnlyViewForAnomaly() throws Exception {
        String executiveToken = loginAndGetToken("executive", "Passw0rd!");

        JsonNode anomalyResp = getJson("/api/anomaly/events", executiveToken, status().isOk());
        assertEquals(20000, anomalyResp.path("code").asInt());
        assertTrue(anomalyResp.path("data").path("summaryOnly").asBoolean());
        assertEquals(0, anomalyResp.path("data").path("events").size());

        JsonNode statusResp = getJson("/api/anomaly/status", executiveToken, status().isOk());
        assertEquals(20000, statusResp.path("code").asInt());
    }

    @Test
    void executiveCannotAccessSecurityConfigButCanViewOpsSummary() throws Exception {
        String executiveToken = loginAndGetToken("executive", "Passw0rd!");

        JsonNode forbiddenResp = getJson("/api/privacy/config", executiveToken, status().isForbidden());
        assertEquals(40300, forbiddenResp.path("code").asInt());

        JsonNode opsResp = getJson("/api/ops-metrics/key-tasks", executiveToken, status().isOk());
        assertEquals(20000, opsResp.path("code").asInt());
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
