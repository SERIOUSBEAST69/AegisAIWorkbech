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
class EmployeeDutySegregationIntegrationTest {

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
    void employee1CanCreateDeleteRequest() throws Exception {
        String token = loginAndGetToken("employee1", "Passw0rd!");

        JsonNode resp = postJson(
            "/api/subject-request/create",
            token,
            Map.of("type", "delete", "comment", "employee1 delete request"),
            status().isOk()
        );
        assertEquals(20000, resp.path("code").asInt(), resp.toString());
    }

    @Test
    void employee2CanCreateAccessButCannotCreateDelete() throws Exception {
        String token = loginAndGetToken("employee2", "Passw0rd!");

        JsonNode allowResp = postJson(
            "/api/subject-request/create",
            token,
            Map.of("type", "access", "comment", "employee2 access request"),
            status().isOk()
        );
        assertEquals(20000, allowResp.path("code").asInt(), allowResp.toString());

        JsonNode denyResp = postJson(
            "/api/subject-request/create",
            token,
            Map.of("type", "delete", "comment", "employee2 delete request"),
            status().isOk()
        );
        assertEquals(40300, denyResp.path("code").asInt(), denyResp.toString());
    }

    @Test
    void employee3IsViewOnlyForSubjectRequest() throws Exception {
        String token = loginAndGetToken("employee3", "Passw0rd!");

        JsonNode denyResp = postJson(
            "/api/subject-request/create",
            token,
            Map.of("type", "access", "comment", "employee3 should be view-only"),
            status().isOk()
        );
        assertEquals(40300, denyResp.path("code").asInt(), denyResp.toString());
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

    private JsonNode postJson(String path, String token, Object body, org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
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
}
