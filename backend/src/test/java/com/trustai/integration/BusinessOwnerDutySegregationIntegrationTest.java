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
class BusinessOwnerDutySegregationIntegrationTest {

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
    void bizowner2CanApproveButCannotReject() throws Exception {
        Long requestId = createBusinessApprovalByEmployee();
        String token = loginAndGetToken("bizowner_2", "Passw0rd!");

        JsonNode approveResp = postJson(
            "/api/approval/approve",
            token,
            Map.of("requestId", requestId, "status", "通过"),
            status().isOk()
        );
        assertEquals(40000, approveResp.path("code").asInt(), approveResp.toString());

        Long requestId2 = createBusinessApprovalByEmployee();
        JsonNode rejectResp = postJson(
            "/api/approval/reject",
            token,
            Map.of("requestId", requestId2, "status", "拒绝"),
            status().isOk()
        );
        assertEquals(40300, rejectResp.path("code").asInt(), rejectResp.toString());
    }

    @Test
    void bizowner3CanRejectButCannotApprove() throws Exception {
        Long requestId = createBusinessApprovalByEmployee();
        String token = loginAndGetToken("bizowner_3", "Passw0rd!");

        JsonNode rejectResp = postJson(
            "/api/approval/reject",
            token,
            Map.of("requestId", requestId, "status", "拒绝"),
            status().isOk()
        );
        assertEquals(40000, rejectResp.path("code").asInt(), rejectResp.toString());

        Long requestId2 = createBusinessApprovalByEmployee();
        JsonNode approveResp = postJson(
            "/api/approval/approve",
            token,
            Map.of("requestId", requestId2, "status", "通过"),
            status().isOk()
        );
        assertEquals(40300, approveResp.path("code").asInt(), approveResp.toString());
    }

    private Long createBusinessApprovalByEmployee() throws Exception {
        Long employeeId = jdbcTemplate.query(
            "SELECT id FROM sys_user WHERE company_id = 1 AND username = 'employee1' ORDER BY id ASC LIMIT 1",
            rs -> rs.next() ? rs.getLong(1) : null
        );
        assertTrue(employeeId != null && employeeId > 0L);

        String suffix = String.valueOf(System.currentTimeMillis() % 100000);
        jdbcTemplate.update(
            "INSERT INTO approval_request(company_id, applicant_id, asset_id, reason, status, approver_id, process_instance_id, task_id, create_time, update_time) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            1L,
            employeeId,
            1L,
            "[BUSINESS] 业务场景审批_" + suffix,
            "待审批",
            null,
            null,
            null
        );

        Long requestId = jdbcTemplate.query(
            "SELECT id FROM approval_request WHERE company_id = 1 AND applicant_id = ? AND reason LIKE ? ORDER BY id DESC LIMIT 1",
            ps -> {
                ps.setLong(1, employeeId);
                ps.setString(2, "%业务场景审批_" + suffix + "%");
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );
        assertTrue(requestId != null && requestId > 0L);
        return requestId;
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

    private JsonNode postJson(String path,
                              String token,
                              Object body,
                              org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
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
