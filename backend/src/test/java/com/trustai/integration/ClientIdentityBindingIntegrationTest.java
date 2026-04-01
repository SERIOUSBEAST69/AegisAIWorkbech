package com.trustai.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class ClientIdentityBindingIntegrationTest {

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
    void registerRejectsMissingHostname() throws Exception {
        JsonNode resp = postJson(
            "/api/client/register",
            Map.of(
                "clientId", "cid-no-host-1",
                "osUsername", "employee1",
                "osType", "Windows",
                "clientVersion", "1.0.0"
            ),
            status().isOk()
        );
        assertEquals(40000, resp.path("code").asInt(), resp.toString());
    }

    @Test
    void registerAndReportReturnDeviceFingerprint() throws Exception {
        String clientId = "cid-fp-" + (System.currentTimeMillis() % 100000);

        JsonNode registerResp = postJson(
            "/api/client/register",
            Map.of(
                "clientId", clientId,
                "hostname", "HOST-FP-1",
                "osUsername", "employee1",
                "osType", "Windows",
                "clientVersion", "1.0.0"
            ),
            status().isOk()
        );
        assertEquals(20000, registerResp.path("code").asInt(), registerResp.toString());
        assertTrue(registerResp.path("data").path("deviceFingerprint").asText().length() >= 8);

        JsonNode reportResp = postJson(
            "/api/client/report",
            Map.of(
                "clientId", clientId,
                "hostname", "HOST-FP-1",
                "osUsername", "employee1",
                "osType", "Windows",
                "clientVersion", "1.0.0",
                "discoveredServices", "[]",
                "shadowAiCount", 0
            ),
            status().isOk()
        );
        assertEquals(20000, reportResp.path("code").asInt(), reportResp.toString());
        assertTrue(reportResp.path("data").path("deviceFingerprint").asText().length() >= 8);
    }

    @Test
    void reportRejectsUnknownUsername() throws Exception {
        JsonNode resp = postJson(
            "/api/client/report",
            Map.of(
                "clientId", "cid-unknown-user-1",
                "hostname", "HOST-UNKNOWN-1",
                "osUsername", "unknown_employee_x",
                "osType", "Windows",
                "clientVersion", "1.0.0",
                "discoveredServices", "[]",
                "shadowAiCount", 0
            ),
            status().isOk()
        );
        assertEquals(40000, resp.path("code").asInt(), resp.toString());
    }

    private JsonNode postJson(String path, Object body, org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        var builder = post(path)
            .header("X-Client-Token", "demo-client-token")
            .header("X-Company-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));

        MvcResult result = mockMvc.perform(builder)
            .andExpect(matcher)
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
