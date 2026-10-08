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
import rw.rra.roomiq.identity.domain.repository.AppUserRepository;
import rw.rra.roomiq.identity.domain.repository.IdentityAuditOutboxRepository;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity-role-api-test",
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
class RolePermissionControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

        @Autowired
        private IdentityAuditOutboxRepository auditOutboxRepository;

    @Test
    void rolePermissionAndScopedUserRoleAssignmentsPersistAndCanBeRevoked() throws Exception {
        String roleBody = """
                {"code":"ROOM_ADMIN","name":"Room administrator","description":"Manage rooms","system":false}
                """;
        String permissionBody = """
                {"code":"ROOM.READ","name":"Read rooms","domain":"ROOM"}
                """;
        String roleResponse = mockMvc.perform(post("/api/v1/roles").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(roleBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("ROOM_ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String permissionResponse = mockMvc.perform(post("/api/v1/permissions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(permissionBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("ROOM.READ"))
                .andReturn().getResponse().getContentAsString();

        String roleId = JsonPath.read(roleResponse, "$.data.id");
        String permissionId = JsonPath.read(permissionResponse, "$.data.id");
        assertAuditEvent("IDENTITY_ROLE_CREATED", UUID.fromString(roleId));
        assertAuditEvent("IDENTITY_PERMISSION_CREATED", UUID.fromString(permissionId));
        String userRoleResponse = mockMvc.perform(post("/api/v1/roles/{roleId}/permissions/{permissionId}", roleId, permissionId)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.permissionCode").value("ROOM.READ"))
                .andReturn().getResponse().getContentAsString();
        String rolePermissionAssignmentId = JsonPath.read(userRoleResponse, "$.data.assignmentId");
        assertAuditEvent("IDENTITY_ROLE_PERMISSION_GRANTED", UUID.fromString(rolePermissionAssignmentId));

        mockMvc.perform(get("/api/v1/roles/{roleId}/permissions", roleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].permissionId").value(permissionId));

        AppUser user = userRepository.saveAndFlush(
                new AppUser("role-target@rra.rw", "Role Target", UserStatus.ACTIVE));
        UUID buildingId = UUID.randomUUID();
        String userRoleBody = """
                {"roleId":"%s","scopeOfficeBuildingId":"%s"}
                """.formatted(roleId, buildingId);
        String assignmentResponse = mockMvc.perform(post("/api/v1/users/{userId}/roles", user.getId())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(userRoleBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roleCode").value("ROOM_ADMIN"))
                .andExpect(jsonPath("$.data.scopeOfficeBuildingId").value(buildingId.toString()))
                .andReturn().getResponse().getContentAsString();
        String userRoleAssignmentId = JsonPath.read(assignmentResponse, "$.data.assignmentId");
        assertAuditEvent("IDENTITY_USER_ROLE_ASSIGNED", UUID.fromString(userRoleAssignmentId));

        mockMvc.perform(get("/api/v1/users/{userId}/roles", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assignmentId").value(userRoleAssignmentId));

        mockMvc.perform(delete("/api/v1/users/{userId}/roles/{assignmentId}", user.getId(), userRoleAssignmentId)
                        .with(csrf()))
                .andExpect(status().isOk());
        assertAuditEvent("IDENTITY_USER_ROLE_REVOKED", UUID.fromString(userRoleAssignmentId));
        mockMvc.perform(delete("/api/v1/roles/{roleId}/permissions/{permissionId}", roleId, permissionId)
                        .with(csrf()))
                .andExpect(status().isOk());
        String revokeEvent = auditOutboxRepository
                .findTopByEventTypeOrderByCreatedAtDesc("IDENTITY_ROLE_PERMISSION_REVOKED").orElseThrow().getPayload();
        org.assertj.core.api.Assertions.assertThat(revokeEvent)
                .contains(roleId, permissionId, "\"schemaVersion\":1");
        mockMvc.perform(get("/api/v1/users/{userId}/roles", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(delete("/api/v1/roles/{roleId}/permissions/{permissionId}", roleId, permissionId)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROLE_PERMISSION_NOT_FOUND"));
    }

    @Test
    void duplicateAssignmentsAndDefinitionsReturnSharedConflictErrors() throws Exception {
        String roleBody = """
                {"code":"DUPLICATE_ROLE","name":"Duplicate Role","system":false}
                """;
        mockMvc.perform(post("/api/v1/roles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(roleBody))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/roles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(roleBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_CODE_CONFLICT"));

        String permissionBody = """
                {"code":"DUPLICATE.READ","name":"Duplicate read"}
                """;
        mockMvc.perform(post("/api/v1/permissions").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(permissionBody))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/permissions").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(permissionBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERMISSION_CODE_CONFLICT"));

        String roleList = mockMvc.perform(get("/api/v1/roles"))
                .andReturn().getResponse().getContentAsString();
        String permissionList = mockMvc.perform(get("/api/v1/permissions"))
                .andReturn().getResponse().getContentAsString();
        String roleId = JsonPath.read(roleList, "$.data[0].id");
        String permissionId = JsonPath.read(permissionList, "$.data[0].id");
        String grantPath = "/api/v1/roles/" + roleId + "/permissions/" + permissionId;

        mockMvc.perform(post(grantPath).with(csrf())).andExpect(status().isCreated());
        mockMvc.perform(post(grantPath).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_PERMISSION_CONFLICT"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        AppUser user = userRepository.saveAndFlush(
                new AppUser("duplicate-role@rra.rw", "Duplicate role", UserStatus.ACTIVE));
        String assignment = "{\"roleId\":\"%s\"}".formatted(roleId);
        String assignmentPath = "/api/v1/users/" + user.getId() + "/roles";
        mockMvc.perform(post(assignmentPath).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(assignment))
                .andExpect(status().isCreated());
        mockMvc.perform(post(assignmentPath).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(assignment))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ROLE_CONFLICT"));
    }

    @Test
    void invalidRoleDefinitionUsesSharedValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/roles").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"bad-code\",\"name\":\"\",\"system\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("code")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("name")));
    }

        @Test
        void malformedJsonUsesSharedBadRequestError() throws Exception {
                mockMvc.perform(post("/api/v1/roles").with(csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                                .andExpect(jsonPath("$.path").value("/api/v1/roles"));
        }

    @Test
    void missingRolePermissionAndUserRoleTargetsReturnStableNotFoundCodes() throws Exception {
        String roleResponse = mockMvc.perform(post("/api/v1/roles").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOT_FOUND_TEST_ROLE\",\"name\":\"Missing target test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID roleId = UUID.fromString(JsonPath.read(roleResponse, "$.data.id"));
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/roles/{roleId}/permissions", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/roles/{roleId}/permissions/{permissionId}", roleId, missingId).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERMISSION_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/users/{userId}/roles", missingId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"" + roleId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/users/{userId}/roles/{assignmentId}", missingId, UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private void assertAuditEvent(String eventType, UUID resourceId) {
        String payload = auditOutboxRepository.findTopByEventTypeOrderByCreatedAtDesc(eventType)
                .orElseThrow().getPayload();
        org.assertj.core.api.Assertions.assertThat(payload)
                .contains(eventType, resourceId.toString(), "\"schemaVersion\":1");
    }
}
