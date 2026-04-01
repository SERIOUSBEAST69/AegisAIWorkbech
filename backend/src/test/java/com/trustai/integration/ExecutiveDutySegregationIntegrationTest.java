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
class ExecutiveDutySegregationIntegrationTest {

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
    void executive2CanViewStatsButCannotOpenAlertList() throws Exception {
        String token = loginAndGetToken("executive_2", "Passw0rd!");

        JsonNode statsResp = getJson("/api/alert-center/stats", token, status().isOk());
        assertEquals(20000, statsResp.path("code").asInt(), statsResp.toString());

        JsonNode listResp = getJson("/api/alert-center/list?page=1&pageSize=10", token, status().isOk());
        assertEquals(40300, listResp.path("code").asInt(), listResp.toString());
    }

    @Test
    void executive3CanViewListButCannotQueryUserHistory() throws Exception {
        String token = loginAndGetToken("executive_3", "Passw0rd!");

        JsonNode listResp = getJson("/api/alert-center/list?page=1&pageSize=10", token, status().isOk());
        assertEquals(20000, listResp.path("code").asInt(), listResp.toString());

        JsonNode userHistoryResp = getJson("/api/alert-center/user-history?limit=10", token, status().isOk());
        assertEquals(40300, userHistoryResp.path("code").asInt(), userHistoryResp.toString());
    }

    @Test
    void executiveCanAccessUserHistory() throws Exception {
        String token = loginAndGetToken("executive", "Passw0rd!");
        JsonNode userHistoryResp = getJson("/api/alert-center/user-history?limit=10", token, status().isOk());
        assertEquals(20000, userHistoryResp.path("code").asInt(), userHistoryResp.toString());
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

    private JsonNode postJson(String path,
                              String token,
                              Object body,
                              org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
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

    private JsonNode getJson(String path,
                             String token,
                             org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        var builder = get(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
            builder.header("X-Company-Id", "1");
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(matcher)
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
