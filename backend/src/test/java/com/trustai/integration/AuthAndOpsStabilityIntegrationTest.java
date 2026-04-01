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
class AuthAndOpsStabilityIntegrationTest {

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

    @Test
    void registrationOptionsEndpointReturnsSuccessPayload() throws Exception {
        JsonNode resp = getJson("/api/auth/registration-options", null);
        assertEquals(20000, resp.path("code").asInt());
        assertTrue(resp.path("data").path("identities").isArray());
        assertTrue(resp.path("data").path("organizations").isArray());
    }

    @Test
    void webVitalsEndpointAcceptsJsonPayload() throws Exception {
        JsonNode resp = postJson("/api/ops-metrics/web-vitals", null, Map.of("name", "LCP", "value", 1234));
        assertEquals(20000, resp.path("code").asInt());
        assertTrue(resp.path("data").path("accepted").asBoolean());
        assertEquals("LCP", resp.path("data").path("metric").asText());
    }

    @Test
    void meEndpointReturnsCurrentUserAfterLogin() throws Exception {
        JsonNode loginResp = postJson("/api/auth/login", null, Map.of("username", "admin", "password", "admin"));
        assertEquals(20000, loginResp.path("code").asInt());
        String token = loginResp.path("data").path("token").asText();
        assertTrue(token != null && !token.isBlank());

        JsonNode meResp = getJson("/api/auth/me", token);
        assertEquals(20000, meResp.path("code").asInt());
        assertEquals("admin", meResp.path("data").path("user").path("username").asText());
    }

    private JsonNode getJson(String path, String token) throws Exception {
        var builder = get(path);
        builder.header("X-Company-Id", "1");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        var builder = post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
        builder.header("X-Company-Id", "1");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
