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
class AwardAndObservabilityIntegrationTest {

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
    void awardSummaryAndComplianceMappingShouldBeAvailableForAdmin() throws Exception {
        String token = loginAsAdmin();
        JsonNode summary = getJson("/api/award/summary", token);
        assertEquals(20000, summary.path("code").asInt(), summary.toString());
        assertTrue(summary.path("data").isObject());

        JsonNode mapping = getJson("/api/award/compliance-mapping", token);
        assertEquals(20000, mapping.path("code").asInt(), mapping.toString());
        assertTrue(mapping.path("data").path("PIPL").isArray());
    }

    @Test
    void webVitalsShouldPersistAndBeQueryable() throws Exception {
        JsonNode ingest = postJson("/api/ops-metrics/web-vitals", null, Map.of(
            "name", "LCP",
            "value", 1200,
            "rating", "good",
            "id", "integration-vital"
        ));
        assertEquals(20000, ingest.path("code").asInt(), ingest.toString());

        String token = loginAsAdmin();
        JsonNode summary = getJson("/api/ops-metrics/web-vitals/summary?days=7", token);
        assertEquals(20000, summary.path("code").asInt(), summary.toString());
        assertTrue(summary.path("data").path("summary").isArray());
    }

    @Test
    void httpHistoryEndpointShouldReturnPayload() throws Exception {
        String token = loginAsAdmin();
        JsonNode resp = getJson("/api/ops-metrics/http-history?days=7", token);
        assertEquals(20000, resp.path("code").asInt(), resp.toString());
        assertTrue(resp.path("data").path("rows").isArray());
    }

    private String loginAsAdmin() throws Exception {
        JsonNode loginResp = postJson("/api/auth/login", null, Map.of("username", "admin", "password", "admin"));
        assertEquals(20000, loginResp.path("code").asInt());
        String token = loginResp.path("data").path("token").asText();
        assertTrue(token != null && !token.isBlank());
        return token;
    }

    private JsonNode getJson(String path, String token) throws Exception {
        var builder = get(path);
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
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
