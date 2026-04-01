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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubjectRequestWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean(name = "securitySchemaInitializer")
    private CommandLineRunner securitySchemaInitializerRunner;

    @MockBean(name = "privacyShieldSchemaInitializer")
    private CommandLineRunner privacyShieldSchemaInitializerRunner;

    @MockBean(name = "awardSchemaInitializer")
    private CommandLineRunner awardSchemaInitializerRunner;

    @Test
    void employeeCannotCreateSubjectRequestForAnotherUser() throws Exception {
        String employeeToken = loginAndGetToken("employee1", "Passw0rd!");
        Long adminId = jdbcTemplate.query(
            "SELECT id FROM sys_user WHERE LOWER(username)='admin' LIMIT 1",
            rs -> rs.next() ? rs.getLong(1) : null
        );
        assertTrue(adminId != null && adminId > 0L);

        JsonNode resp = postJson(
            "/api/subject-request/create",
            employeeToken,
            Map.of("userId", adminId, "type", "access", "comment", "forged user"),
            status().isOk()
        );
        assertEquals(40300, resp.path("code").asInt(), resp.toString());
    }

    @Test
    void processMustUseLegalTransitionAndCurrentOperatorAsHandler() throws Exception {
        String employeeToken = loginAndGetToken("employee1", "Passw0rd!");
        JsonNode createResp = postJson(
            "/api/subject-request/create",
            employeeToken,
            Map.of("type", "delete", "comment", "need erase"),
            status().isOk()
        );
        assertEquals(20000, createResp.path("code").asInt(), createResp.toString());
        long requestId = createResp.path("data").path("id").asLong();
        assertTrue(requestId > 0L);

        String adminToken = loginAndGetToken("admin", "admin");
        Long secopsId = jdbcTemplate.query(
            "SELECT id FROM sys_user WHERE LOWER(username)='secops' LIMIT 1",
            rs -> rs.next() ? rs.getLong(1) : null
        );
        assertTrue(secopsId != null && secopsId > 0L);

        JsonNode forgedHandler = postJson(
            "/api/subject-request/process",
            adminToken,
            Map.of("id", requestId, "status", "processing", "handlerId", secopsId, "result", "forged handler"),
            status().isOk()
        );
        assertEquals(40300, forgedHandler.path("code").asInt(), forgedHandler.toString());

        Long adminId = jdbcTemplate.query(
            "SELECT id FROM sys_user WHERE LOWER(username)='admin' LIMIT 1",
            rs -> rs.next() ? rs.getLong(1) : null
        );
        assertTrue(adminId != null && adminId > 0L);

        JsonNode illegalTransition = postJson(
            "/api/subject-request/process",
            adminToken,
            Map.of("id", requestId, "status", "done", "handlerId", adminId, "result", "skip processing"),
            status().isOk()
        );
        assertEquals(40000, illegalTransition.path("code").asInt(), illegalTransition.toString());

        JsonNode legalTransition = postJson(
            "/api/subject-request/process",
            adminToken,
            Map.of("id", requestId, "status", "processing", "handlerId", adminId, "result", "accepted"),
            status().isOk()
        );
        assertEquals(20000, legalTransition.path("code").asInt(), legalTransition.toString());
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
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(matcher)
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
