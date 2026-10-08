package rw.rra.roomiq.identity.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import rw.rra.roomiq.identity.domain.entity.AppUser;
import rw.rra.roomiq.identity.domain.entity.UserStatus;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.identity.domain.repository.AppUserRepository;
import rw.rra.roomiq.identity.domain.repository.IdentityAuditOutboxRepository;
import rw.rra.roomiq.identity.domain.repository.UserPrivilegeRepository;
import rw.rra.roomiq.identity.domain.service.PrivilegeManagementService;

import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity-privilege-api-test",
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
class PrivilegeControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private UserPrivilegeRepository privilegeRepository;

        @Autowired
        private IdentityAuditOutboxRepository auditOutboxRepository;

        @Autowired
        private PrivilegeManagementService privilegeService;

    @Test
    void evaluatesCgPrivilegeAtValidityWindowBoundariesAndRevokesHistorically() throws Exception {
        AppUser user = activeUser("cg-evaluation@rra.rw");
        Instant start = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(Duration.ofHours(2));
        String body = """
                {"privilegeCode":"CG_BOOKING","validFrom":"%s","validTo":"%s"}
                """.formatted(start, end);

        String response = mockMvc.perform(post("/api/v1/users/{userId}/privileges", user.getId())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse().getContentAsString();
        String privilegeId = JsonPath.read(response, "$.data.id");
        assertAuditEvent("IDENTITY_PRIVILEGE_GRANTED", privilegeId);

        evaluate(user, start.minusSeconds(1), false);
        evaluate(user, start, true);
        evaluate(user, end.minusSeconds(1), true);
        evaluate(user, end, false);

        mockMvc.perform(get("/api/v1/users/{userId}/privileges", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].effectiveNow").value(false));

        mockMvc.perform(delete("/api/v1/users/{userId}/privileges/{privilegeId}", user.getId(), privilegeId)
                        .with(csrf()))
                .andExpect(status().isOk());
        assertAuditEvent("IDENTITY_PRIVILEGE_REVOKED", privilegeId);
        evaluate(user, start.plusSeconds(10), false);
        org.assertj.core.api.Assertions.assertThat(privilegeRepository.findById(java.util.UUID.fromString(privilegeId)))
                .get().extracting(privilege -> privilege.isActive()).isEqualTo(false);

        mockMvc.perform(post("/api/v1/users/{userId}/privileges", user.getId())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsOverlappingGrantButAllowsAdjacentValidityWindow() throws Exception {
        AppUser user = activeUser("cg-overlap@rra.rw");
        String firstGrant = """
                {"privilegeCode":"CG_BOOKING","validFrom":"2026-11-01T10:00:00Z","validTo":"2026-11-01T11:00:00Z"}
                """;
        String overlapping = """
                {"privilegeCode":"CG_BOOKING","validFrom":"2026-11-01T10:30:00Z","validTo":"2026-11-01T12:00:00Z"}
                """;
        String adjacent = """
                {"privilegeCode":"CG_BOOKING","validFrom":"2026-11-01T11:00:00Z","validTo":"2026-11-01T12:00:00Z"}
                """;

        mockMvc.perform(post("/api/v1/users/{userId}/privileges", user.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(firstGrant))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/users/{userId}/privileges", user.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(overlapping))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRIVILEGE_GRANT_CONFLICT"));
        mockMvc.perform(post("/api/v1/users/{userId}/privileges", user.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(adjacent))
                .andExpect(status().isCreated());
    }

    @Test
    void inactiveUserNeverEvaluatesAsPrivilegedAndInvalidWindowIsRejected() throws Exception {
        AppUser user = userRepository.saveAndFlush(
                new AppUser("cg-inactive@rra.rw", "Inactive CG User", UserStatus.SUSPENDED));
        String body = """
                {"privilegeCode":"CG_BOOKING"}
                """;
        mockMvc.perform(post("/api/v1/users/{userId}/privileges", user.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        evaluate(user, Instant.now(), false);

        String invalidWindow = """
                {"privilegeCode":"CG_BOOKING","validFrom":"2026-12-01T12:00:00Z","validTo":"2026-12-01T11:00:00Z"}
                """;
        mockMvc.perform(post("/api/v1/users/{userId}/privileges", user.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(invalidWindow))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRIVILEGE_VALIDITY_WINDOW"));
    }

    @Test
    void missingPrivilegeResourcesReturnStableNotFoundCodes() throws Exception {
        AppUser user = activeUser("cg-missing-privilege@rra.rw");
        java.util.UUID missingUserId = java.util.UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{userId}/privileges", missingUserId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/users/{userId}/privileges/{privilegeId}", user.getId(),
                        java.util.UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> privilegeService.revoke(user.getId(), java.util.UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo("USER_PRIVILEGE_NOT_FOUND"));
    }

    private AppUser activeUser(String email) {
        return userRepository.saveAndFlush(new AppUser(email, "CG Test User", UserStatus.ACTIVE));
    }

        private void assertAuditEvent(String eventType, String resourceId) {
                String payload = auditOutboxRepository.findTopByEventTypeOrderByCreatedAtDesc(eventType)
                                .orElseThrow().getPayload();
                org.assertj.core.api.Assertions.assertThat(payload)
                                .contains(eventType, resourceId, "\"schemaVersion\":1")
                                .doesNotContain("cg-evaluation@rra.rw", "Good-password-9");
        }

    private void evaluate(AppUser user, Instant at, boolean expected) throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/privileges/evaluate", user.getId())
                        .queryParam("privilegeCode", "CG_BOOKING")
                        .queryParam("at", at.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(expected));
    }
}
