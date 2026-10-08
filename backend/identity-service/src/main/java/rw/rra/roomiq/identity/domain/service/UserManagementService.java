package rw.rra.roomiq.identity.domain.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.identity.domain.dto.ChangeUserStatusRequest;
import rw.rra.roomiq.identity.domain.dto.CreateUserRequest;
import rw.rra.roomiq.identity.domain.dto.UpdateUserRequest;
import rw.rra.roomiq.identity.domain.dto.UserListQuery;
import rw.rra.roomiq.identity.domain.dto.UserListResponse;
import rw.rra.roomiq.identity.domain.dto.UserResponse;
import rw.rra.roomiq.identity.domain.entity.AppUser;
import rw.rra.roomiq.identity.domain.entity.UserRole;
import rw.rra.roomiq.identity.domain.entity.UserStatus;
import rw.rra.roomiq.identity.domain.repository.AppUserRepository;

import java.util.UUID;

@Service
@Transactional
public class UserManagementService {
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityAuditService auditService;

    public UserManagementService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                                 IdentityAuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public UserResponse create(CreateUserRequest request) {
        ensureUnique(request.email(), request.nationalId(), request.phone(), null);
        AppUser user = new AppUser(request.email().trim(), request.fullName().trim(), UserStatus.PENDING);
        user.updateProfile(null, normalize(request.nationalId()), normalize(request.phone()), null,
                normalize(request.displayName()), request.departmentId(), request.officeBuildingId());
        if (request.password() != null) {
            user.replacePasswordHash(passwordEncoder.encode(request.password()));
        }
        UserResponse response = UserResponse.from(userRepository.saveAndFlush(user));
        auditService.recordCurrentActor("IDENTITY_USER_CREATED", "USER", response.id(),
            request.officeBuildingId(), "SUCCESS");
        return response;
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional(readOnly = true)
    public UserListResponse list(UserListQuery query, boolean scopedAdminView) {
        Specification<AppUser> specification = (root, criteria, builder) -> builder.conjunction();
        if (query.status() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("status"), query.status()));
        }
        if (query.departmentId() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("departmentId"), query.departmentId()));
        }
        if (query.officeBuildingId() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("officeBuildingId"), query.officeBuildingId()));
        }
        if (query.search() != null && !query.search().isBlank()) {
            String search = "%" + query.search().trim().toLowerCase() + "%";
            specification = specification.and((root, criteria, builder) -> builder.or(
                    builder.like(builder.lower(root.get("email")), search),
                    builder.like(builder.lower(root.get("fullName")), search),
                    builder.like(builder.lower(root.get("displayName")), search)));
        }
        if (scopedAdminView) {
            UUID buildingId = query.officeBuildingId();
            specification = specification.and((root, criteria, builder) -> {
                var secretaryAssignment = criteria.subquery(Integer.class);
                var secretaryRoleAssignment = secretaryAssignment.from(UserRole.class);
                secretaryAssignment.select(builder.literal(1)).where(
                        builder.equal(secretaryRoleAssignment.get("user").get("id"), root.get("id")),
                        builder.equal(secretaryRoleAssignment.get("role").get("code"), "SECRETARY"),
                        builder.equal(secretaryRoleAssignment.get("scopeOfficeBuildingId"), buildingId));

                var privilegedAssignment = criteria.subquery(Integer.class);
                var privilegedRoleAssignment = privilegedAssignment.from(UserRole.class);
                privilegedAssignment.select(builder.literal(1)).where(
                        builder.equal(privilegedRoleAssignment.get("user").get("id"), root.get("id")),
                        privilegedRoleAssignment.get("role").get("code").in("ADMIN", "SUPER_ADMIN"));

                var anyAssignment = criteria.subquery(Integer.class);
                var anyRoleAssignment = anyAssignment.from(UserRole.class);
                anyAssignment.select(builder.literal(1)).where(
                        builder.equal(anyRoleAssignment.get("user").get("id"), root.get("id")));

                return builder.or(
                        builder.and(builder.exists(secretaryAssignment), builder.not(builder.exists(privilegedAssignment))),
                        builder.and(builder.equal(root.get("status"), UserStatus.PENDING),
                                builder.not(builder.exists(anyAssignment))));
            });
        }

        Page<AppUser> page = userRepository.findAll(specification,
                PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new UserListResponse(page.getContent().stream().map(UserResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public UserResponse update(UUID id, UpdateUserRequest request) {
        AppUser user = findUser(id);
        ensureUnique(request.email(), request.nationalId(), request.phone(), id);
        user.updateProfile(normalize(request.email()), normalize(request.nationalId()), normalize(request.phone()),
                normalize(request.fullName()), normalize(request.displayName()),
                request.departmentId(), request.officeBuildingId());
        if (request.password() != null && !request.password().isBlank()) {
            user.replacePasswordHash(passwordEncoder.encode(request.password()));
        }
        UserResponse response = UserResponse.from(userRepository.saveAndFlush(user));
        auditService.recordCurrentActor("IDENTITY_USER_UPDATED", "USER", id,
            user.getOfficeBuildingId(), "SUCCESS");
        return response;
    }

    public UserResponse changeStatus(UUID id, ChangeUserStatusRequest request) {
        AppUser user = findUser(id);
        user.updateStatus(request.status());
        UserResponse response = UserResponse.from(userRepository.saveAndFlush(user));
        auditService.recordCurrentActor("IDENTITY_USER_STATUS_CHANGED", "USER", id,
            user.getOfficeBuildingId(), "SUCCESS");
        return response;
    }

    public void delete(UUID id) {
        AppUser user = findUser(id);
        user.markDeleted();
        userRepository.saveAndFlush(user);
        auditService.recordCurrentActor("IDENTITY_USER_SOFT_DELETED", "USER", id,
            user.getOfficeBuildingId(), "SUCCESS");
    }

    private AppUser findUser(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found"));
    }

    private void ensureUnique(String email, String nationalId, String phone, UUID excludeId) {
        email = normalize(email);
        nationalId = normalize(nationalId);
        phone = normalize(phone);
        boolean emailTaken = email != null && (excludeId == null
                ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, excludeId));
        boolean nationalIdTaken = nationalId != null && (excludeId == null
                ? userRepository.existsByNationalId(nationalId)
                : userRepository.existsByNationalIdAndIdNot(nationalId, excludeId));
        boolean phoneTaken = phone != null && (excludeId == null
                ? userRepository.existsByPhone(phone)
                : userRepository.existsByPhoneAndIdNot(phone, excludeId));
        if (emailTaken || nationalIdTaken || phoneTaken) {
                throw new DomainException(HttpStatus.CONFLICT, "DUPLICATE_USER_IDENTIFIER",
                    "A user with one of these identifiers already exists");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}