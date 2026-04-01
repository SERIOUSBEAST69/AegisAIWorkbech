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
class DataAdminDutySegregationIntegrationTest {

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
    void dataadmin2CanCreateButCannotDeleteAsset() throws Exception {
        String token = loginAndGetToken("dataadmin_2", "Passw0rd!");
        String name = "dataadmin2_asset_" + (System.currentTimeMillis() % 100000);

        JsonNode createResp = postJson(
            "/api/data-asset/register",
            token,
            Map.of(
                "name", name,
                "type", "table",
                "sensitivityLevel", "high",
                "location", "mysql://demo/asset_" + name,
                "description", "dataadmin_2 create"
            ),
            status().isOk()
        );
        assertEquals(20000, createResp.path("code").asInt(), createResp.toString());

        Long assetId = jdbcTemplate.query(
            "SELECT id FROM data_asset WHERE company_id = 1 AND name = ? ORDER BY id DESC LIMIT 1",
            ps -> ps.setString(1, name),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        assertTrue(assetId != null && assetId > 0L);

        JsonNode deleteResp = postJson(
            "/api/data-asset/delete",
            token,
            Map.of("id", assetId),
            status().isOk()
        );
        assertEquals(40300, deleteResp.path("code").asInt(), deleteResp.toString());
    }

    @Test
    void dataadmin3CanReviewTodoButCannotMutateAsset() throws Exception {
        String token = loginAndGetToken("dataadmin_3", "Passw0rd!");

        JsonNode registerResp = postJson(
            "/api/data-asset/register",
            token,
            Map.of(
                "name", "dataadmin3_forbidden_" + (System.currentTimeMillis() % 100000),
                "type", "table",
                "sensitivityLevel", "high",
                "location", "mysql://demo/forbidden",
                "description", "forbidden"
            ),
            status().isOk()
        );
        assertEquals(40300, registerResp.path("code").asInt(), registerResp.toString());

        JsonNode todoResp = getJson("/api/approval/todo", token, status().isOk());
        assertEquals(20000, todoResp.path("code").asInt(), todoResp.toString());
    }

    @Test
    void dataadmin2CannotAccessApprovalTodo() throws Exception {
        String token = loginAndGetToken("dataadmin_2", "Passw0rd!");
        JsonNode todoResp = getJson("/api/approval/todo", token, status().isOk());
        assertEquals(40300, todoResp.path("code").asInt(), todoResp.toString());
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

    private JsonNode getJson(String path, String token, org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
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
