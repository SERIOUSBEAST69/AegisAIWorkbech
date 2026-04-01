package com.trustai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleEndpointAuthorizationMatrixIntegrationTest {

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

    private String adminToken;
    private String secopsToken;
    private String dataAdminToken;
    private String businessOwnerToken;
    private String executiveToken;
    private String aiBuilderToken;
    private String employeeToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginFirstAvailable(List.of("admin"), "admin");
        secopsToken = loginFirstAvailable(List.of("secops"), "Passw0rd!");
        dataAdminToken = loginFirstAvailable(List.of("dataadmin"), "Passw0rd!");
        businessOwnerToken = loginFirstAvailable(List.of("bizowner", "businessowner"), "Passw0rd!");
        executiveToken = loginFirstAvailable(List.of("executive"), "Passw0rd!");
        aiBuilderToken = loginFirstAvailable(List.of("aibuilder"), "Passw0rd!");
        employeeToken = tryEmployeeLogins();
    }

    @Test
    @DisplayName("role-endpoint matrix: 20+ core APIs must enforce least privilege")
    void roleEndpointAuthorizationMatrix() throws Exception {
        List<EndpointRule> rules = List.of(
            rule("/api/auth/me", allRoles()),
            rule("/api/dashboard/stats", allRoles()),
            rule("/api/dashboard/workbench", allRoles()),
            rule("/api/alert-center/list", allRoles()),
            rule("/api/alert-center/stats", allRoles()),
            rule("/api/alert-center/user-history", allRoles()),
            rule("/api/client/list", roles(Role.ADMIN, Role.SECOPS, Role.EMPLOYEE)),
            rule("/api/client/stats", roles(Role.ADMIN, Role.SECOPS, Role.EMPLOYEE)),
            rule("/api/client/queue", roles(Role.ADMIN, Role.SECOPS)),
            rule("/api/data-asset/list", roles(Role.ADMIN, Role.SECOPS, Role.DATA_ADMIN)),
            rule("/api/approval/list", roles(Role.ADMIN, Role.DATA_ADMIN, Role.BUSINESS_OWNER, Role.EMPLOYEE)),
            rule("/api/approval/page", roles(Role.ADMIN, Role.DATA_ADMIN, Role.BUSINESS_OWNER, Role.EMPLOYEE)),
            rule("/api/approval/todo", roles(Role.ADMIN, Role.DATA_ADMIN, Role.BUSINESS_OWNER)),
            rule("/api/security/events", roles(Role.ADMIN, Role.SECOPS)),
            rule("/api/security/stats", roles(Role.ADMIN, Role.SECOPS)),
            rule("/api/security/rules", roles(Role.ADMIN, Role.SECOPS)),
            rule("/api/ai/monitor/summary", roles(Role.ADMIN, Role.SECOPS)),
            rule("/api/ai/monitor/trend", roles(Role.ADMIN, Role.SECOPS)),
            rule("/api/ai/monitor/logs", roles(Role.ADMIN, Role.SECOPS)),
            rule("/api/ai-risk/list", roles(Role.ADMIN, Role.SECOPS)),
            rule("/api/ai-risk/score?service=chatgpt", roles(Role.ADMIN, Role.SECOPS)),
            rule("/api/ai/adversarial/meta", roles(Role.ADMIN, Role.SECOPS)),
            rule("/api/governance-change/page", roles(Role.ADMIN, Role.SECOPS)),
            rule("/api/audit-report/generate", roles(Role.ADMIN))
        );

        for (EndpointRule endpointRule : rules) {
            for (Role role : Role.values()) {
                int expected = endpointRule.allowedRoles.contains(role) ? 200 : 403;
                int actual = mockMvc.perform(
                        get(endpointRule.path)
                            .header("Authorization", bearer(tokenOf(role)))
                            .header("X-Company-Id", "1")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus();

                assertEquals(
                    expected,
                    actual,
                    () -> "path=" + endpointRule.path + ", role=" + role + ", expected=" + expected + ", actual=" + actual
                );
            }
        }
    }

    private String tryEmployeeLogins() throws Exception {
        List<String> usernames = Arrays.asList("employee1", "employee");
        for (String username : usernames) {
            try {
                return login(username, "Passw0rd!");
            } catch (AssertionError ignored) {
                // Try fallback account name for compatibility with older fixtures.
            }
        }
        throw new AssertionError("Cannot login as employee using known test accounts");
    }

    private String loginFirstAvailable(List<String> usernames, String password) throws Exception {
        for (String username : usernames) {
            try {
                return login(username, password);
            } catch (AssertionError ignored) {
                // Continue with fallback username.
            }
        }
        throw new AssertionError("Cannot login with candidates: " + usernames);
    }

    private String login(String username, String password) throws Exception {
        String payload = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String body = mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        String token = root.path("data").path("token").asText();
        assertTrue(token != null && !token.isBlank(), "token should not be blank");
        return token;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String tokenOf(Role role) {
        return switch (role) {
            case ADMIN -> adminToken;
            case SECOPS -> secopsToken;
            case DATA_ADMIN -> dataAdminToken;
            case BUSINESS_OWNER -> businessOwnerToken;
            case EXECUTIVE -> executiveToken;
            case AI_BUILDER -> aiBuilderToken;
            case EMPLOYEE -> employeeToken;
        };
    }

    private static EnumSet<Role> allRoles() {
        return EnumSet.allOf(Role.class);
    }

    private static EnumSet<Role> roles(Role... roles) {
        return roles.length == 0 ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(Arrays.asList(roles));
    }

    private static EndpointRule rule(String path, EnumSet<Role> allowedRoles) {
        return new EndpointRule(path, allowedRoles);
    }

    private enum Role {
        ADMIN,
        SECOPS,
        DATA_ADMIN,
        BUSINESS_OWNER,
        EXECUTIVE,
        AI_BUILDER,
        EMPLOYEE
    }

    private record EndpointRule(String path, EnumSet<Role> allowedRoles) {
    }
}
