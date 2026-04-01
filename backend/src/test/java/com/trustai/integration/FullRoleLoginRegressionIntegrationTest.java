package com.trustai.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
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
class FullRoleLoginRegressionIntegrationTest {

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
    void allSevenRolesCanLoginAndFetchSessionProfile() throws Exception {
        Map<String, String[]> roles = new LinkedHashMap<>();
        roles.put("ADMIN", new String[] {"admin", "admin"});
        roles.put("EXECUTIVE", new String[] {"executive", "Passw0rd!"});
        roles.put("SECOPS", new String[] {"secops", "Passw0rd!"});
        roles.put("DATA_ADMIN", new String[] {"dataadmin", "Passw0rd!"});
        roles.put("AI_BUILDER", new String[] {"aibuilder", "Passw0rd!"});
        roles.put("BUSINESS_OWNER", new String[] {"bizowner", "Passw0rd!"});
        roles.put("EMPLOYEE", new String[] {"employee1", "Passw0rd!"});

        for (Map.Entry<String, String[]> entry : roles.entrySet()) {
            String expectedRole = entry.getKey();
            String username = entry.getValue()[0];
            String password = entry.getValue()[1];

            JsonNode loginResp;
            if ("EMPLOYEE".equals(expectedRole)) {
                loginResp = loginEmployeeWithFallback(password);
                username = loginResp.path("data").path("user").path("username").asText();
            } else {
                loginResp = postJson("/api/auth/login", null, Map.of("username", username, "password", password));
            }
            assertEquals(20000, loginResp.path("code").asInt(), "login failed for role=" + expectedRole);

            String token = loginResp.path("data").path("token").asText();
            assertTrue(token != null && !token.isBlank(), "token missing for role=" + expectedRole);

            JsonNode roleCodeFromLogin = loginResp.path("data").path("user").path("roleCode");
            assertEquals(expectedRole, roleCodeFromLogin.asText(), "roleCode mismatch at login for role=" + expectedRole);

            JsonNode meResp = getJson("/api/auth/me", token);
            assertEquals(20000, meResp.path("code").asInt(), "me endpoint failed for role=" + expectedRole);
            assertEquals(username, meResp.path("data").path("user").path("username").asText(), "username mismatch for role=" + expectedRole);
            assertEquals(expectedRole, meResp.path("data").path("user").path("roleCode").asText(), "roleCode mismatch on /me for role=" + expectedRole);
        }
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

    private JsonNode loginEmployeeWithFallback(String password) throws Exception {
        for (String candidate : new String[] {"employee1", "employee"}) {
            JsonNode loginResp = postJson("/api/auth/login", null, Map.of("username", candidate, "password", password));
            if (loginResp.path("code").asInt() == 20000) {
                return loginResp;
            }
        }
        fail("employee login failed for both employee1 and employee");
        return null;
    }
}
