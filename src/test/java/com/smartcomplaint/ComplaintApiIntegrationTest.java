
package com.smartcomplaint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_URL=jdbc:h2:mem:complaint-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "DB_USERNAME=sa",
        "DB_PASSWORD=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-schema.sql",
        "JWT_SECRET=a-very-secure-jwt-test-secret-key-of-at-least-32-chars-long",
        "FRONTEND_URL=https://complaints.example.com"
})
@AutoConfigureMockMvc
@ActiveProfiles("production")
class ComplaintApiIntegrationTest {

    private static final String ADMIN_PASSWORD = "admin-test-password";
    private static final String FRONTEND_ORIGIN = "https://complaints.example.com";
    private static final @NonNull MediaType JSON =
            new MediaType("application", "json");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    @Autowired
    private ObjectMapper objectMapper;

    private int adminId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM complaints");
        jdbcTemplate.update("DELETE FROM users");

        jdbcTemplate.update(
                "INSERT INTO users (full_name, email, password, role) VALUES (?, ?, ?, 'ADMIN')",
                "Integration Admin",
                "admin-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode(ADMIN_PASSWORD));

        adminId = Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE role = 'ADMIN'",
                        Integer.class),
                "Integration admin was not inserted");
    }

    @Test
    void userCanRegisterLoginSubmitTrackAndSeeAdminResolution()
            throws Exception {

        String email = "citizen-" + UUID.randomUUID() + "@example.test";
        register(email);
        UserSession user = login(email, "citizen-password");

        MvcResult submission = mockMvc.perform(
                        post("/api/complaints")
                                .header("Authorization", bearer(user.token()))
                                .contentType(JSON)
                                .content(jsonContent(Map.of(
                                        "user_id", user.id(),
                                        "title", "Leaking water pipe",
                                        "description",
                                        "Water is leaking near the entrance.",
                                        "category", "Water"))))
                .andExpect(status().isCreated())
                .andReturn();

        int complaintId = objectMapper
                .readTree(submission.getResponse().getContentAsString())
                .get("complaint_id")
                .asInt();

        mockMvc.perform(get("/api/complaints")
                        .param("userId", String.valueOf(user.id()))
                        .header("Authorization", bearer(user.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(complaintId))
                .andExpect(jsonPath("$[0].user_id").value(user.id()))
                .andExpect(jsonPath("$[0].title").value("Leaking water pipe"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mockMvc.perform(get("/api/complaints")
                        .header("Authorization", bearer(user.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(complaintId))
                .andExpect(jsonPath("$[0].user_id").value(user.id()));

        UserSession admin = loginById(
                adminId, currentAdminEmail(), ADMIN_PASSWORD);

        mockMvc.perform(get("/api/admin/complaints")
                        .param("adminId", String.valueOf(adminId))
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(complaintId))
                .andExpect(jsonPath("$[0].user_id").value(user.id()));

        mockMvc.perform(put("/api/admin/complaints/{id}", complaintId)
                        .header("Authorization", bearer(admin.token()))
                        .contentType(JSON)
                        .content(jsonContent(Map.of(
                                "adminId", adminId,
                                "status", "RESOLVED",
                                "admin_remark", "Repair completed."))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/complaints")
                        .param("userId", String.valueOf(user.id()))
                        .header("Authorization", bearer(user.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(complaintId))
                .andExpect(jsonPath("$[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$[0].admin_remark")
                        .value("Repair completed."));
    }

    @Test
    void usersCannotSubmitForOrReadAnotherUsersComplaints()
            throws Exception {

        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.test";
        register(ownerEmail);
        UserSession owner = login(ownerEmail, "citizen-password");

        String otherEmail = "other-" + UUID.randomUUID() + "@example.test";
        register(otherEmail);
        UserSession other = login(otherEmail, "citizen-password");

        MvcResult submission = mockMvc.perform(
                        post("/api/complaints")
                                .header("Authorization", bearer(owner.token()))
                                .contentType(JSON)
                                .content(jsonContent(Map.of(
                                        "user_id", owner.id(),
                                        "title", "Private complaint",
                                        "description",
                                        "Only the owner should see this.",
                                        "category", "Other"))))
                .andExpect(status().isCreated())
                .andReturn();

        int complaintId = objectMapper
                .readTree(submission.getResponse().getContentAsString())
                .get("complaint_id")
                .asInt();

        // A user cannot read another user's complaints.
        mockMvc.perform(get("/api/complaints")
                        .param("userId", String.valueOf(owner.id()))
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isForbidden());

        // A user cannot submit a complaint on another user's behalf.
        mockMvc.perform(post("/api/complaints")
                        .header("Authorization", bearer(other.token()))
                        .contentType(JSON)
                        .content(jsonContent(Map.of(
                                "user_id", owner.id(),
                                "title", "Spoofed complaint",
                                "description", "Must be rejected.",
                                "category", "Other"))))
                .andExpect(status().isForbidden());

        // A normal user cannot view admin complaints.
        mockMvc.perform(get("/api/admin/complaints")
                        .param("adminId", String.valueOf(adminId))
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isForbidden());

        // A normal user cannot change complaint status.
        mockMvc.perform(put("/api/admin/complaints/{id}", complaintId)
                        .header("Authorization", bearer(other.token()))
                        .contentType(JSON)
                        .content(jsonContent(Map.of(
                                "adminId", adminId,
                                "status", "RESOLVED",
                                "admin_remark", "Unauthorized update"))))
                .andExpect(status().isForbidden());

        // The complaint must remain unchanged after the rejected request.
        String currentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM complaints WHERE id = ?",
                String.class,
                complaintId);

        assertEquals("PENDING", currentStatus);

        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT id FROM complaints WHERE id = ?",
                Integer.class,
                complaintId));
    }

    @Test
    void rejectsInvalidCredentialsAndProtectedRequestsWithoutJwt()
            throws Exception {

        mockMvc.perform(post("/api/login")
                        .contentType(JSON)
                        .content(jsonContent(Map.of(
                                "email", "missing@example.test",
                                "password", "incorrect-password"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/complaints"))
                .andExpect(status().isForbidden());
    }

    @Test
    void liveServerStartsAndProtectsComplaintEndpoint() {
        ResponseEntity<String> response =
                testRestTemplate.getForEntity(
                        "/api/complaints", String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void healthEndpointChecksDatabaseConnectivity() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
    }

    @Test
    void permitsProductionFrontendCorsPreflight() throws Exception {
        mockMvc.perform(options("/api/login")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(content().string(""))
                .andExpect(result ->
                        assertEquals(
                                FRONTEND_ORIGIN,
                                result.getResponse()
                                        .getHeader("Access-Control-Allow-Origin")));
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/register")
                        .contentType(JSON)
                        .content(jsonContent(Map.of(
                                "full_name", "Integration Citizen",
                                "email", email,
                                "password", "citizen-password"))))
                .andExpect(status().isCreated());
    }

    private UserSession login(String email, String password)
            throws Exception {

        MvcResult result = mockMvc.perform(post("/api/login")
                        .contentType(JSON)
                        .content(jsonContent(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString());
        JsonNode user = body.get("user");

        return new UserSession(
                user.get("id").asInt(),
                body.get("token").asText());
    }

    private UserSession loginById(int id, String email, String password)
            throws Exception {

        assertEquals(
                id,
                Objects.requireNonNull(jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE email = ?",
                        Integer.class,
                        email)));

        return login(email, password);
    }

    private String currentAdminEmail() {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE id = ?",
                String.class,
                adminId));
    }

    @NonNull
    private String jsonContent(Object value)
            throws JsonProcessingException {

        return Objects.requireNonNull(
                objectMapper.writeValueAsString(value));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record UserSession(int id, String token) {
    }
}
