package com.trustai.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.repository.AssetEsRepository;
import com.trustai.repository.AuditLogEsRepository;
import com.trustai.repository.ModelEsRepository;
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
class TenantConsistencyIntegrationTest {

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

    @MockBean
    private AssetEsRepository assetEsRepository;

    @MockBean
    private ModelEsRepository modelEsRepository;

    @MockBean
    private AuditLogEsRepository auditLogEsRepository;

    @Test
    void rejectMismatchedCompanyHeader() throws Exception {
        String token = loginAndGetToken("admin", "admin");

        JsonNode resp = getJson(
            "/api/client/list",
            token,
            "2",
            status().isForbidden()
        );
        assertEquals(40300, resp.path("code").asInt());
    }

    @Test
    void rejectMismatchedCompanyQuery() throws Exception {
        String token = loginAndGetToken("secops", "Passw0rd!");

        JsonNode resp = getJson(
            "/api/alert-center/list?companyId=99&page=1&pageSize=10",
            token,
            null,
            status().isForbidden()
        );
        assertEquals(40300, resp.path("code").asInt());
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        JsonNode loginResp = postJson(
            "/api/auth/login",
            Map.of("username", username, "password", password),
            status().isOk()
        );
        assertEquals(20000, loginResp.path("code").asInt());
        String token = loginResp.path("data").path("token").asText();
        assertTrue(token != null && !token.isBlank());
        return token;
    }

    private JsonNode postJson(String path, Object body, org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(matcher)
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path,
                             String token,
                             String companyHeader,
                             org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        var builder = get(path).contentType(MediaType.APPLICATION_JSON);
        builder.header("Authorization", "Bearer " + token);
        if (companyHeader != null) {
            builder.header("X-Company-Id", companyHeader);
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(matcher)
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
