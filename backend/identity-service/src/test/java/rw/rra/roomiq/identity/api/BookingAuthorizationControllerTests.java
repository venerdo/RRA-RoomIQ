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
    void reservationLifecycleAuthorizationEnforcesOrganizerAndCurrentBuildingDepartmentScope() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID otherBuildingId = UUID.randomUUID();
        AppUser organizer = createUser("lifecycle-organizer-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, departmentId, buildingId);
        AppUser colleague = createUser("lifecycle-colleague-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, departmentId, buildingId);
        AppUser reviewer = createUser("lifecycle-reviewer-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, null, buildingId);
        AppUser outOfScopeReviewer = createUser("lifecycle-out-of-scope-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, null, otherBuildingId);

        Role organizerRole = createRoleWithPermissions("LIFECYCLE_OWNER", "BOOKING_LIFECYCLE_OWN");
        Role reviewerRole = createRoleWithPermissions("LIFECYCLE_ADMIN", "BOOKING_LIFECYCLE_MANAGE");
        userRoles.saveAndFlush(new UserRole(organizer, organizerRole, buildingId, null));
        userRoles.saveAndFlush(new UserRole(colleague, organizerRole, buildingId, null));
        userRoles.saveAndFlush(new UserRole(reviewer, reviewerRole, buildingId, null));
        userRoles.saveAndFlush(new UserRole(outOfScopeReviewer, reviewerRole, otherBuildingId, null));

        mockMvc.perform(reservationLifecycle(organizer.getId(), organizer.getId(), departmentId, buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorUserId").value(organizer.getId().toString()));
        mockMvc.perform(reservationLifecycle(colleague.getId(), organizer.getId(), departmentId, buildingId))
                .andExpect(status().isForbidden());
        mockMvc.perform(reservationLifecycle(organizer.getId(), organizer.getId(), UUID.randomUUID(), buildingId))
                .andExpect(status().isForbidden());
        mockMvc.perform(reservationLifecycle(organizer.getId(), organizer.getId(), departmentId, otherBuildingId))
                .andExpect(status().isForbidden());
        mockMvc.perform(reservationLifecycle(reviewer.getId(), organizer.getId(), departmentId, buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceOwnerUserId").value(organizer.getId().toString()));
        mockMvc.perform(reservationLifecycle(
                        outOfScopeReviewer.getId(), organizer.getId(), departmentId, buildingId))
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
        mockMvc.perform(bookingDirect(directBooker.getId().toString(), departmentId, buildingId, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorUserId").value(directBooker.getId().toString()))
                .andExpect(jsonPath("$.resourceOwnerUserId").value(directBooker.getId().toString()))
                .andExpect(jsonPath("$.resourceOwnerDisplayName").value("Booking Authorization Test"))
                .andExpect(jsonPath("$.directBookingAuthority").value("ADMIN"));
    }

    @Test
    void adminAndSuperAdminDirectAuthorityNeverAllowsSelfApproval() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AppUser admin = createUser("direct-admin-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, null, buildingId);
        Role adminRole = createRoleWithPermissions(
                "DIRECT_ADMIN", "BOOKING_DIRECT_CREATE", "BOOKING_APPROVE");
        userRoles.saveAndFlush(new UserRole(admin, adminRole, buildingId, null));

        AppUser superAdmin = createUser("direct-super-admin-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, null, null);
        Role superAdminRole = createRoleWithPermissions(
                "SUPER_ADMIN", "IDENTITY_SYSTEM_ADMIN", "BOOKING_DIRECT_CREATE", "BOOKING_APPROVE");
        userRoles.saveAndFlush(new UserRole(superAdmin, superAdminRole, null, null));

        for (AppUser privilegedUser : List.of(admin, superAdmin)) {
            String actor = privilegedUser.getId().toString();
            mockMvc.perform(bookingDirect(actor, departmentId, buildingId, false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.actorUserId").value(actor))
                    .andExpect(jsonPath("$.directBookingAuthority").value("ADMIN"));
            mockMvc.perform(bookingDecision(actor, "APPROVE", privilegedUser.getId(), buildingId))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void eligibleSecretaryDirectBookingRequiresCurrentScopeAndVipPrivilege() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AppUser secretary = createUser("direct-secretary-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, departmentId, buildingId);
        Role secretaryRole = createRoleWithPermissions("DIRECT_SECRETARY", "BOOKING_REQUEST_CREATE");
        userRoles.saveAndFlush(new UserRole(secretary, secretaryRole, buildingId, null));
        String actor = secretary.getId().toString();

        mockMvc.perform(bookingDirect(actor, departmentId, buildingId, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directBookingAuthority").value("SECRETARY"));
        mockMvc.perform(bookingDirect(actor, UUID.randomUUID(), buildingId, false))
                .andExpect(status().isForbidden());
        mockMvc.perform(bookingDirect(actor, departmentId, buildingId, true))
                .andExpect(status().isForbidden());

        privileges.saveAndFlush(new UserPrivilege(secretary, "CG_BOOKING", null,
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(60), true));
        mockMvc.perform(bookingDirect(actor, departmentId, buildingId, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directBookingAuthority").value("SECRETARY"));
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
    void cancellationAuthorizationReturnsTrustedOwnerOrScopedAdministratorAuthority() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID otherBuildingId = UUID.randomUUID();
        AppUser owner = createUser("cancel-owner-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, UUID.randomUUID(), buildingId);
        AppUser admin = createUser("cancel-admin-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, null, buildingId);
        AppUser outOfScopeAdmin = createUser("cancel-out-of-scope-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, null, otherBuildingId);
        AppUser systemAdmin = createUser("cancel-system-admin-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, null, null);
        Role ownerRole = createRoleWithPermissions("BOOKING_OWNER_CANCEL_" + UUID.randomUUID(),
                "BOOKING_CANCEL_OWN");
        Role adminRole = createRoleWithPermissions("BOOKING_ADMIN_CANCEL_" + UUID.randomUUID(),
                "BOOKING_CANCEL");
        Role systemAdminRole = createRoleWithPermissions("BOOKING_SYSTEM_ADMIN_CANCEL_" + UUID.randomUUID(),
                "IDENTITY_SYSTEM_ADMIN");
        userRoles.saveAndFlush(new UserRole(owner, ownerRole, buildingId, null));
        userRoles.saveAndFlush(new UserRole(admin, adminRole, buildingId, null));
        userRoles.saveAndFlush(new UserRole(outOfScopeAdmin, adminRole, otherBuildingId, null));
        userRoles.saveAndFlush(new UserRole(systemAdmin, systemAdminRole, null, null));

        mockMvc.perform(bookingDecision(owner.getId().toString(), "CANCEL", owner.getId(), buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorUserId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.resourceOwnerUserId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.cancellationAuthority").value("OWNER"));
        mockMvc.perform(bookingDecision(admin.getId().toString(), "CANCEL", owner.getId(), buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationAuthority").value("ADMIN"));
        mockMvc.perform(bookingDecision(outOfScopeAdmin.getId().toString(), "CANCEL", owner.getId(), buildingId))
                .andExpect(status().isForbidden());
        mockMvc.perform(bookingDecision(owner.getId().toString(), "CANCEL", owner.getId(), otherBuildingId))
                .andExpect(status().isForbidden());
        mockMvc.perform(bookingDecision(systemAdmin.getId().toString(), "CANCEL", owner.getId(), buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationAuthority").value("SYSTEM_ADMIN"));
    }

    @Test
    void privateMeetingReadIsLimitedToOrganizerAndScopedReviewers() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AppUser organizer = createUser("meeting-owner-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, departmentId, buildingId);
        organizer.updateProfile(null, null, null, null, "Trusted Organizer", departmentId, buildingId);
        users.saveAndFlush(organizer);
        AppUser reviewer = createUser("meeting-reviewer-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, null, buildingId);
        Role reviewerRole = createRoleWithPermissions("MEETING_REVIEWER_" + UUID.randomUUID(),
                "BOOKING_APPROVE");
        userRoles.saveAndFlush(new UserRole(reviewer, reviewerRole, buildingId, null));

        mockMvc.perform(meetingAuthorization(organizer.getId(), "READ", organizer.getId(),
                        departmentId, buildingId, "PRIVATE", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizerDisplayName").value("Trusted Organizer"));
        mockMvc.perform(meetingAuthorization(reviewer.getId(), "READ", organizer.getId(),
                        departmentId, buildingId, "PRIVATE", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizerUserId").value(organizer.getId().toString()));

        AppUser colleague = createUser("private-colleague-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, UUID.randomUUID(), buildingId);
        mockMvc.perform(meetingAuthorization(colleague.getId(), "READ", organizer.getId(),
                        departmentId, buildingId, "PRIVATE", null))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalMeetingReadAllowsOnlyActiveUsersInTheSameBuilding() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AppUser organizer = createUser("internal-meeting-owner-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, departmentId, buildingId);
        AppUser sameBuildingUser = createUser("same-building-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, UUID.randomUUID(), buildingId);
        AppUser otherBuildingUser = createUser("other-building-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, UUID.randomUUID(), UUID.randomUUID());
        AppUser inactiveUser = createUser("inactive-building-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.SUSPENDED, UUID.randomUUID(), buildingId);

        mockMvc.perform(meetingAuthorization(sameBuildingUser.getId(), "READ", organizer.getId(),
                        departmentId, buildingId, "INTERNAL", null))
                .andExpect(status().isOk());
        mockMvc.perform(meetingAuthorization(sameBuildingUser.getId(), "READ", organizer.getId(),
                        departmentId, buildingId, "PUBLIC", null))
                .andExpect(status().isForbidden());
        mockMvc.perform(meetingAuthorization(otherBuildingUser.getId(), "READ", organizer.getId(),
                        departmentId, buildingId, "INTERNAL", null))
                .andExpect(status().isForbidden());
        mockMvc.perform(meetingAuthorization(inactiveUser.getId(), "READ", organizer.getId(),
                        departmentId, buildingId, "INTERNAL", null))
                .andExpect(status().isForbidden());
    }

    @Test
    void meetingManagementValidatesInternalParticipantStatusAndBuilding() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AppUser organizer = createUser("meeting-manager-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, departmentId, buildingId);
        AppUser internalParticipant = createUser("meeting-user-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, UUID.randomUUID(), buildingId);
        internalParticipant.updateProfile(null, null, null, null, "Trusted Participant",
                internalParticipant.getDepartmentId(), buildingId);
        users.saveAndFlush(internalParticipant);
        AppUser otherBuildingParticipant = createUser("meeting-remote-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, UUID.randomUUID(), UUID.randomUUID());
        AppUser inactiveParticipant = createUser("meeting-inactive-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.DISABLED, UUID.randomUUID(), buildingId);

        mockMvc.perform(meetingAuthorization(organizer.getId(), "MANAGE", organizer.getId(),
                        departmentId, buildingId, "PRIVATE", internalParticipant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantUserId").value(internalParticipant.getId().toString()))
                .andExpect(jsonPath("$.participantDisplayName").value("Trusted Participant"));
        mockMvc.perform(meetingAuthorization(organizer.getId(), "MANAGE", organizer.getId(),
                        departmentId, buildingId, "PRIVATE", otherBuildingParticipant.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(meetingAuthorization(organizer.getId(), "MANAGE", organizer.getId(),
                        departmentId, buildingId, "PRIVATE", inactiveParticipant.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(meetingAuthorization(internalParticipant.getId(), "MANAGE", organizer.getId(),
                        departmentId, buildingId, "PRIVATE", null))
                .andExpect(status().isForbidden());
    }

    @Test
    void meetingAuthorizationRejectsMissingFactsAndParticipantReadTargets() throws Exception {
        AppUser user = createUser("meeting-invalid-" + UUID.randomUUID() + "@rra.rw",
                UserStatus.ACTIVE, UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/internal/authorization/meeting")
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"READ","buildingId":"%s","organizerUserId":"%s",
                                 "departmentId":"%s","visibility":"PRIVATE","participantUserId":"%s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                                UUID.randomUUID())))
                .andExpect(status().isBadRequest());
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder meetingAuthorization(
            UUID tokenSubject, String action, UUID organizerUserId, UUID departmentId,
            UUID buildingId, String visibility, UUID participantUserId) {
        String participant = participantUserId == null ? "" :
                ",\"participantUserId\":\"" + participantUserId + "\"";
        return post("/api/v1/internal/authorization/meeting")
                .with(jwt().jwt(token -> token.subject(tokenSubject.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"action":"%s","organizerUserId":"%s","departmentId":"%s",
                         "buildingId":"%s","visibility":"%s"%s}
                        """.formatted(action, organizerUserId, departmentId, buildingId, visibility, participant));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bookingDirect(
            String tokenSubject, UUID departmentId, UUID buildingId, boolean vipRoom) {
        return post(AUTHORIZATION_PATH)
                .with(jwt().jwt(token -> token.subject(tokenSubject)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"action":"DIRECT_CREATE","departmentId":"%s","buildingId":"%s","vipRoom":%s}
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reservationLifecycle(
            UUID tokenSubject, UUID organizerUserId, UUID departmentId, UUID buildingId) {
        return post(AUTHORIZATION_PATH)
                .with(jwt().jwt(token -> token.subject(tokenSubject.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"action":"RESERVATION_LIFECYCLE","resourceOwnerUserId":"%s",
                         "departmentId":"%s","buildingId":"%s"}
                        """.formatted(organizerUserId, departmentId, buildingId));
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
