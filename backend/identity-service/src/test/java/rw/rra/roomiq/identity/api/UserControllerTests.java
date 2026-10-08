package rw.rra.roomiq.identity.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import rw.rra.roomiq.identity.domain.entity.AppUser;
import rw.rra.roomiq.identity.domain.repository.AppUserRepository;
import rw.rra.roomiq.identity.domain.repository.IdentityAuditOutboxRepository;
import rw.rra.roomiq.identity.domain.security.IdentityUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity-api-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "roomiq.auth.jwt-secret=test-only-signing-key-at-least-32-bytes-long",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.flyway.clean-disabled=false")
@WithMockUser(authorities = "IDENTITY_SYSTEM_ADMIN")
class UserControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

        @Autowired
        private IdentityAuditOutboxRepository auditOutboxRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private IdentityUserDetailsService userDetailsService;

    @Test
    void createReadListUpdateStatusAndSoftDeleteUser() throws Exception {
        String createBody = """
                {
                  "email": "api-lifecycle@rra.rw",
                  "fullName": "API Lifecycle User",
                  "displayName": "Lifecycle",
                  "password": "Correct-horse-battery-9",
                  "departmentId": "7c2b1f0e-0d0d-4c60-b0de-7fae9d73e7a1"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .header("X-Correlation-ID", "identity-user-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("api-lifecycle@rra.rw"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(response, "$.data.id");
        String auditPayload = auditOutboxRepository
                .findTopByEventTypeOrderByCreatedAtDesc("IDENTITY_USER_CREATED").orElseThrow().getPayload();
        org.assertj.core.api.Assertions.assertThat(auditPayload)
                .contains("identity-user-create", id, "\"schemaVersion\":1")
                .doesNotContain("Correct-horse-battery-9", "api-lifecycle@rra.rw");
        var saved = userRepository.findById(java.util.UUID.fromString(id)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getPasswordHash())
                .isNotEqualTo("Correct-horse-battery-9").startsWith("$2a$12$");
        org.assertj.core.api.Assertions.assertThat(passwordEncoder.matches(
                "Correct-horse-battery-9", saved.getPasswordHash())).isTrue();
        org.assertj.core.api.Assertions.assertThat(userDetailsService.loadUserByUsername(saved.getEmail()).isEnabled())
                .isFalse();

        mockMvc.perform(get("/api/v1/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("API Lifecycle User"));

        mockMvc.perform(get("/api/v1/users")
                        .queryParam("search", "lifecycle")
                        .queryParam("page", "0")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.users[0].email").value("api-lifecycle@rra.rw"));

        mockMvc.perform(patch("/api/v1/users/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Updated Lifecycle User","password":"Updated-password-9"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Updated Lifecycle User"));
        assertAuditEvent("IDENTITY_USER_UPDATED", id);
        AppUser updated = userRepository.findById(java.util.UUID.fromString(id)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(passwordEncoder.matches(
                "Updated-password-9", updated.getPasswordHash())).isTrue();

        mockMvc.perform(patch("/api/v1/users/{id}/status", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE","reason":"verified"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertAuditEvent("IDENTITY_USER_STATUS_CHANGED", id);
        org.assertj.core.api.Assertions.assertThat(userDetailsService.loadUserByUsername(saved.getEmail()).isEnabled())
                .isTrue();

        mockMvc.perform(delete("/api/v1/users/{id}", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertAuditEvent("IDENTITY_USER_SOFT_DELETED", id);

        mockMvc.perform(get("/api/v1/users/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void statusChangesFailClosedForSuspendedAccounts() throws Exception {
        String response = mockMvc.perform(post("/api/v1/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"status-check@rra.rw\",\"fullName\":\"Status Check\",\"password\":\"Strong-password-9\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.data.id");
        AppUser saved = userRepository.findById(java.util.UUID.fromString(id)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(userDetailsService.loadUserByUsername(saved.getEmail()).isEnabled())
                .isFalse();

        mockMvc.perform(patch("/api/v1/users/{id}/status", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());
        var suspendedDetails = userDetailsService.loadUserByUsername(saved.getEmail());
        org.assertj.core.api.Assertions.assertThat(suspendedDetails.isEnabled()).isFalse();
        org.assertj.core.api.Assertions.assertThat(suspendedDetails.isAccountNonLocked()).isFalse();
    }

    @Test
    void invalidCreateReturnsSharedValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-email","fullName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/v1/users"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("fullName")));
    }

    @Test
    void duplicateEmailReturnsSharedConflictError() throws Exception {
        String body = """
                {"email":"duplicate-api@rra.rw","fullName":"Duplicate User"}
                """;
        mockMvc.perform(post("/api/v1/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USER_IDENTIFIER"));
    }

        private void assertAuditEvent(String eventType, String resourceId) {
                String payload = auditOutboxRepository.findTopByEventTypeOrderByCreatedAtDesc(eventType)
                                .orElseThrow().getPayload();
                org.assertj.core.api.Assertions.assertThat(payload)
                                .contains(eventType, resourceId, "\"schemaVersion\":1")
                                .doesNotContain("api-lifecycle@rra.rw", "Correct-horse-battery-9");
        }
}
