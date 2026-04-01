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
class AiBuilderDutySegregationIntegrationTest {

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
    void aibuilder2CanSubmitCheckButCannotViewEvents() throws Exception {
        String token = loginAndGetToken("aibuilder_2", "Passw0rd!");

        JsonNode checkResp = postJson(
            "/api/anomaly/check",
            token,
            Map.of(
                "employee_id", "employee1",
                "department", "研发",
                "ai_service", "通义千问",
                "hour_of_day", 11,
                "day_of_week", 1,
                "message_length", 180,
                "topic_code", 0,
                "session_duration_min", 8,
                "is_new_service", 0
            ),
            status().isOk()
        );
        assertTrue(checkResp.path("code").asInt() != 40300, checkResp.toString());

        JsonNode eventsResp = getJson("/api/anomaly/events", token, status().isOk());
        assertEquals(40300, eventsResp.path("code").asInt(), eventsResp.toString());
    }

    @Test
    void aibuilder3CanViewEventsButCannotSubmitCheck() throws Exception {
        String token = loginAndGetToken("aibuilder_3", "Passw0rd!");

        JsonNode eventsResp = getJson("/api/anomaly/events", token, status().isOk());
        assertEquals(20000, eventsResp.path("code").asInt(), eventsResp.toString());

        JsonNode checkResp = postJson(
            "/api/anomaly/check",
            token,
            Map.of(
                "employee_id", "employee1",
                "department", "研发",
                "ai_service", "文心一言",
                "hour_of_day", 10,
                "day_of_week", 2,
                "message_length", 260,
                "topic_code", 1,
                "session_duration_min", 12,
                "is_new_service", 0
            ),
            status().isOk()
        );
        assertEquals(40300, checkResp.path("code").asInt(), checkResp.toString());
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
