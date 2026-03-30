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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecOpsPolicyWorkflowIntegrationTest {

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

    @MockBean(name = "companySchemaInitializer")
    private CommandLineRunner companySchemaInitializerRunner;

    @MockBean(name = "awardSchemaInitializer")
    private CommandLineRunner awardSchemaInitializerRunner;

    @Test
    void secopsShouldManagePolicyWithSecondPasswordAndDataVisibleInList() throws Exception {
        ensurePolicyTable();
        String secopsToken = loginAndGetToken("secops", "Passw0rd!");
        String policyName = "SECOPS_POLICY_" + System.currentTimeMillis();

        JsonNode saveResp = postJson(
            "/api/policy/save",
            secopsToken,
            Map.of(
                "name", policyName,
                "ruleContent", "{\"keywords\":[\"身份证号\"]}",
                "scope", "ai_prompt",
                "status", "ACTIVE",
                "confirmPassword", "Passw0rd!"
            )
        );
        assertEquals(20000, saveResp.path("code").asInt(), saveResp.toString());

        JsonNode listResp = getJson("/api/policy/list?name=" + policyName, secopsToken);
        assertEquals(20000, listResp.path("code").asInt(), listResp.toString());
        JsonNode list = listResp.path("data");
        assertTrue(list.isArray(), listResp.toString());
        assertTrue(list.size() > 0, listResp.toString());
        long policyId = list.get(0).path("id").asLong();
        assertTrue(policyId > 0L, listResp.toString());

        JsonNode deleteResp = postJson(
            "/api/policy/delete",
            secopsToken,
            Map.of("id", policyId, "confirmPassword", "Passw0rd!")
        );
        assertEquals(20000, deleteResp.path("code").asInt(), deleteResp.toString());
    }

    private void ensurePolicyTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS compliance_policy (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              company_id BIGINT NOT NULL,
              name VARCHAR(128) NOT NULL,
              rule_content CLOB,
              scope VARCHAR(64),
              status INT DEFAULT 1,
              version INT DEFAULT 1,
              create_time TIMESTAMP,
              update_time TIMESTAMP
            )
            """);
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        JsonNode resp = postJson("/api/auth/login", null, Map.of("username", username, "password", password));
        assertEquals(20000, resp.path("code").asInt(), resp.toString());
        String token = resp.path("data").path("token").asText();
        assertTrue(token != null && !token.isBlank());
        return token;
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

    private JsonNode getJson(String path, String token) throws Exception {
        var builder = get(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
