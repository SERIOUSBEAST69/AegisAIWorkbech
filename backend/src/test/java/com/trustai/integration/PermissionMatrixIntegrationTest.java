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
class PermissionMatrixIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean(name = "securitySchemaInitializer")
    private CommandLineRunner securitySchemaInitializerRunner;

    @MockBean(name = "privacyShieldSchemaInitializer")
    private CommandLineRunner privacyShieldSchemaInitializerRunner;

    @Test
    void adminCanAccessUserListButSecopsCannot() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin");
        JsonNode adminResp = getJson("/api/user/list", adminToken, status().isOk());
        assertEquals(20000, adminResp.path("code").asInt());

        String secopsToken = loginAndGetToken("secops", "Passw0rd!");
        JsonNode secopsResp = getJson("/api/user/list", secopsToken, status().isForbidden());
        assertEquals(40300, secopsResp.path("code").asInt());
    }

    @Test
    void secopsCanAccessRiskEventsButEmployeeCannot() throws Exception {
        String secopsToken = loginAndGetToken("secops", "Passw0rd!");
        JsonNode secopsResp = getJson("/api/risk-event/list", secopsToken, status().isOk());
        assertEquals(20000, secopsResp.path("code").asInt());

        String employeeToken = loginAndGetToken("employee", "Passw0rd!");
        JsonNode employeeResp = getJson("/api/risk-event/list", employeeToken, status().isForbidden());
        assertEquals(40300, employeeResp.path("code").asInt());
    }

    @Test
    void employeeCanListOwnApprovalsButCannotAccessOperatorTodo() throws Exception {
        String employeeToken = loginAndGetToken("employee", "Passw0rd!");
        JsonNode ownListResp = getJson("/api/approval/list", employeeToken, status().isOk());
        assertEquals(20000, ownListResp.path("code").asInt());

        JsonNode todoResp = getJson("/api/approval/todo", employeeToken, status().isForbidden());
        assertEquals(40300, todoResp.path("code").asInt());
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
