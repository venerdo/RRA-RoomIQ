package rw.rra.roomiq.identity.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import rw.rra.roomiq.identity.domain.entity.AppUser;
import rw.rra.roomiq.identity.domain.entity.Permission;
import rw.rra.roomiq.identity.domain.entity.Role;
import rw.rra.roomiq.identity.domain.entity.RolePermission;
import rw.rra.roomiq.identity.domain.entity.UserPrivilege;
import rw.rra.roomiq.identity.domain.entity.UserRole;
import rw.rra.roomiq.identity.domain.entity.UserStatus;
import rw.rra.roomiq.identity.domain.repository.AppUserRepository;
import rw.rra.roomiq.identity.domain.repository.PermissionRepository;
import rw.rra.roomiq.identity.domain.repository.RolePermissionRepository;
import rw.rra.roomiq.identity.domain.repository.RoleRepository;
import rw.rra.roomiq.identity.domain.repository.UserPrivilegeRepository;
import rw.rra.roomiq.identity.domain.repository.UserRoleRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity-booking-authorization-test",
        "spring.datasource.username=sa",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "roomiq.auth.jwt-secret=test-only-signing-key-at-least-32-bytes-long",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.flyway.clean-disabled=false")
class BookingAuthorizationControllerTests {
    private static final String AUTHORIZATION_PATH = "/api/v1/internal/authorization/booking";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PermissionRepository permissions;

    @Autowired
    private RolePermissionRepository rolePermissions;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private UserPrivilegeRepository privileges;

    @Test
    void requiresAnAuthenticatedIdentityTokenAndAnActiveCurrentAccount() throws Exception {
        AppUser active = createUser("active-" + UUID.randomUUID() + "@rra.rw", UserStatus.ACTIVE, null, null);
        AppUser suspended = createUser("suspended-" + UUID.randomUUID() + "@rra.rw", UserStatus.SUSPENDED, null, null);

        mockMvc.perform(post(AUTHORIZATION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"AUTHENTICATE\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(AUTHORIZATION_PATH)
                        .with(jwt().jwt(token -> token.subject(active.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"AUTHENTICATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorUserId").value(active.getId().toString()));
        mockMvc.perform(post(AUTHORIZATION_PATH)
                        .with(jwt().jwt(token -> token.subject(suspended.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"AUTHENTICATE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestCreationUsesTheAuthenticatedUserAndChecksDepartmentBuildingAndCurrentPrivilege()
            throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AppUser secretary = createUser("secretary-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, departmentId, buildingId);
        Role secretaryRole = createRoleWithPermissions("SECRETARY", "BOOKING_REQUEST_CREATE");
        userRoles.saveAndFlush(new UserRole(secretary, secretaryRole, buildingId, null));
        String token = secretary.getId().toString();

        mockMvc.perform(bookingRequest(token, departmentId, buildingId, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorUserId").value(token));
        mockMvc.perform(bookingRequest(token, UUID.randomUUID(), buildingId, false))
                .andExpect(status().isForbidden());
        mockMvc.perform(bookingRequest(token, departmentId, UUID.randomUUID(), false))
                .andExpect(status().isForbidden());
        mockMvc.perform(bookingRequest(token, departmentId, buildingId, true))
                .andExpect(status().isForbidden());

        privileges.saveAndFlush(new UserPrivilege(secretary, "CG_BOOKING", null,
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(60), true));
        mockMvc.perform(bookingRequest(token, departmentId, buildingId, true))
                .andExpect(status().isOk());
    }

    @Test
    void requestListAndReadDecisionsEnforceRequesterAndReviewerBuildingScope() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID otherBuildingId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AppUser requester = createUser("requester-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, departmentId, buildingId);
        AppUser reviewer = createUser("reviewer-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, null, buildingId);
        AppUser outOfScopeReviewer = createUser("out-of-scope-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, null, otherBuildingId);
        Role requesterRole = createRoleWithPermissions("REQUESTER", "BOOKING_REQUEST_CREATE");
        Role reviewerRole = createRoleWithPermissions("REQUEST_REVIEWER", "BOOKING_APPROVE");
        userRoles.saveAndFlush(new UserRole(requester, requesterRole, buildingId, null));
        userRoles.saveAndFlush(new UserRole(reviewer, reviewerRole, buildingId, null));
        userRoles.saveAndFlush(new UserRole(outOfScopeReviewer, reviewerRole, otherBuildingId, null));

        String requesterId = requester.getId().toString();
        String reviewerId = reviewer.getId().toString();

        mockMvc.perform(bookingRequestList(requesterId, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorUserId").value(requesterId));
        mockMvc.perform(bookingRequestList(requesterId, buildingId))
                .andExpect(status().isForbidden());
        mockMvc.perform(bookingRequestList(reviewerId, buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorUserId").value(reviewerId));
        mockMvc.perform(bookingRequestRead(requesterId, requester.getId(), buildingId))
                .andExpect(status().isOk());
        mockMvc.perform(bookingRequestRead(reviewerId, requester.getId(), buildingId))
                .andExpect(status().isOk());
        mockMvc.perform(bookingRequestRead(outOfScopeReviewer.getId().toString(), requester.getId(), buildingId))
                .andExpect(status().isForbidden());
    }

    @Test
    void directBookingPermissionDoesNotGrantBookingRequestCreation() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AppUser directBooker = createUser("direct-booker-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, departmentId, buildingId);
        Role directBookingRole = createRoleWithPermissions("DIRECT_BOOKER", "BOOKING_DIRECT_CREATE");
        userRoles.saveAndFlush(new UserRole(directBooker, directBookingRole, buildingId, null));

        mockMvc.perform(bookingRequest(directBooker.getId().toString(), departmentId, buildingId, false))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyTheRequestingActiveUserCanSubmitTheirDraft() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AppUser requester = createUser("draft-owner-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, departmentId, buildingId);
        AppUser colleague = createUser("draft-colleague-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, departmentId, buildingId);
        Role requesterRole = createRoleWithPermissions("DRAFT_REQUESTER", "BOOKING_REQUEST_CREATE");
        userRoles.saveAndFlush(new UserRole(requester, requesterRole, buildingId, null));
        userRoles.saveAndFlush(new UserRole(colleague, requesterRole, buildingId, null));

        mockMvc.perform(bookingRequestSubmit(requester.getId().toString(), requester.getId(),
                        departmentId, buildingId, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorUserId").value(requester.getId().toString()));
        mockMvc.perform(bookingRequestSubmit(colleague.getId().toString(), requester.getId(),
                        departmentId, buildingId, false))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvalRequiresScopedPermissionAndCannotApproveOwnRequest() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID otherBuildingId = UUID.randomUUID();
        AppUser approver = createUser("approver-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, null, buildingId);
        AppUser requester = createUser("requester-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, UUID.randomUUID(), buildingId);
        AppUser otherBuildingRequester = createUser("other-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, UUID.randomUUID(), otherBuildingId);
        Role approvalRole = createRoleWithPermissions("BOOKING_ADMIN", "BOOKING_APPROVE");
        userRoles.saveAndFlush(new UserRole(approver, approvalRole, buildingId, null));
        String approverToken = approver.getId().toString();

        mockMvc.perform(bookingDecision(approverToken, "APPROVE", requester.getId(), buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorUserId").value(approverToken))
                .andExpect(jsonPath("$.resourceOwnerUserId").value(requester.getId().toString()))
                .andExpect(jsonPath("$.resourceOwnerDisplayName").value("Booking Authorization Test"));
        mockMvc.perform(bookingDecision(approverToken, "APPROVE", approver.getId(), buildingId))
                .andExpect(status().isForbidden());
        mockMvc.perform(bookingDecision(approverToken, "APPROVE", otherBuildingRequester.getId(), otherBuildingId))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedOrIrrelevantAuthorizationFactsAreRejected() throws Exception {
        AppUser user = createUser("validation-" + UUID.randomUUID() + "@rra.rw", UserStatus.ACTIVE, null, null);

        mockMvc.perform(post(AUTHORIZATION_PATH)
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"AUTHENTICATE\",\"resourceOwnerUserId\":\""
                                + user.getId() + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(AUTHORIZATION_PATH)
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REQUEST_CREATE\",\"buildingId\":\""
                                + UUID.randomUUID() + "\",\"departmentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bookingRequest(
            String tokenSubject, UUID departmentId, UUID buildingId, boolean vipRoom) {
        return post(AUTHORIZATION_PATH)
                .with(jwt().jwt(token -> token.subject(tokenSubject)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"action":"REQUEST_CREATE","departmentId":"%s","buildingId":"%s","vipRoom":%s}
                        """.formatted(departmentId, buildingId, vipRoom));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bookingDecision(
            String tokenSubject, String action, UUID resourceOwnerUserId, UUID buildingId) {
        return post(AUTHORIZATION_PATH)
                .with(jwt().jwt(token -> token.subject(tokenSubject)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"action":"%s","resourceOwnerUserId":"%s","buildingId":"%s"}
                        """.formatted(action, resourceOwnerUserId, buildingId));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bookingRequestList(
            String tokenSubject, UUID buildingId) {
        String building = buildingId == null ? "" : ",\"buildingId\":\"" + buildingId + "\"";
        return post(AUTHORIZATION_PATH)
                .with(jwt().jwt(token -> token.subject(tokenSubject)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"REQUEST_LIST\"" + building + "}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bookingRequestSubmit(
            String tokenSubject, UUID requesterUserId, UUID departmentId, UUID buildingId, boolean vipRoom) {
        return post(AUTHORIZATION_PATH)
                .with(jwt().jwt(token -> token.subject(tokenSubject)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"action":"REQUEST_SUBMIT","resourceOwnerUserId":"%s","departmentId":"%s",
                         "buildingId":"%s","vipRoom":%s}
                        """.formatted(requesterUserId, departmentId, buildingId, vipRoom));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bookingRequestRead(
            String tokenSubject, UUID requesterUserId, UUID buildingId) {
        return post(AUTHORIZATION_PATH)
                .with(jwt().jwt(token -> token.subject(tokenSubject)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"action":"REQUEST_READ","resourceOwnerUserId":"%s","buildingId":"%s"}
                        """.formatted(requesterUserId, buildingId));
    }

    private AppUser createUser(String email, UserStatus status, UUID departmentId, UUID buildingId) {
        AppUser user = new AppUser(email, "Booking Authorization Test", status);
        user.updateProfile(null, null, null, null, null, departmentId, buildingId);
        return users.saveAndFlush(user);
    }

    private Role createRoleWithPermissions(String roleCode, String... permissionCodes) {
        Role role = roles.saveAndFlush(new Role(roleCode, roleCode, true));
        List.of(permissionCodes).forEach(code -> {
            Permission permission = permissions.findByCodeIgnoreCase(code)
                    .orElseGet(() -> permissions.saveAndFlush(new Permission(code, code)));
            rolePermissions.saveAndFlush(new RolePermission(role, permission));
        });
        return role;
    }
}
