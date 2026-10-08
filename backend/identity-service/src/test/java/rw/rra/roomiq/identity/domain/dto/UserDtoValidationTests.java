package rw.rra.roomiq.identity.domain.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import rw.rra.roomiq.common.web.ApiError;
import rw.rra.roomiq.common.web.GlobalExceptionHandler;
import rw.rra.roomiq.common.web.ValidationError;
import rw.rra.roomiq.identity.domain.entity.AppUser;
import rw.rra.roomiq.identity.domain.entity.UserStatus;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserDtoValidationTests {
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validator = null;
    }

    @Test
    void validCreateRequestHasNoViolations() {
        CreateUserRequest request = new CreateUserRequest(
                "secretary@rra.rw", "NID-001", "+250788000000",
            "A-secure-password-9", "Marie Uwimana", "Marie",
                null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void passwordPolicyRejectsWeakAndOverlongCredentials() {
        CreateUserRequest weak = new CreateUserRequest(
                "weak@rra.rw", null, null, "alllowercasepassword9", "Weak User", null, null, null);
        CreateUserRequest tooLong = new CreateUserRequest(
                "long@rra.rw", null, null, "Strong-password-9" + "A".repeat(60), "Long User", null, null, null);

        assertThat(validator.validate(weak)).anyMatch(violation -> violation.getPropertyPath().toString().equals("password"));
        assertThat(validator.validate(tooLong)).anyMatch(violation -> violation.getPropertyPath().toString().equals("password"));
    }

    @Test
    void invalidCreateRequestProducesSharedApiErrorShape() {
        CreateUserRequest request = new CreateUserRequest(
                "not-an-email", null, null, "short", "", null, null, null);

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();

        ResponseEntity<ApiError> response = new GlobalExceptionHandler()
                .handleConstraintViolation(new ConstraintViolationException(violations), request("/api/v1/users"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONSTRAINT_VIOLATION");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/users");
        assertThat(response.getBody().correlationId()).isNotBlank();
        assertThat(response.getBody().validationErrors())
                .extracting(ValidationError::field)
                .contains("email", "fullName", "password");
    }

    @Test
    void statusAndListingRequestsValidateTheirContracts() {
        assertThat(validator.validate(new ChangeUserStatusRequest(null, null))).isNotEmpty();
        assertThat(validator.validate(new UserListQuery("x".repeat(201), UserStatus.ACTIVE, null, null, 0, 20)))
                .isNotEmpty();
        assertThat(new UserListQuery(null, null, null, null, 0, 0).size()).isEqualTo(20);
    }

    @Test
    void userResponseDoesNotExposePasswordHash() {
        AppUser user = new AppUser("safe@rra.rw", "Safe User", UserStatus.ACTIVE);
        UserResponse response = UserResponse.from(user);

        assertThat(response).hasNoNullFieldsOrPropertiesExcept(
                "id", "nationalId", "phone", "displayName", "departmentId",
                "officeBuildingId", "lastLoginAt", "version", "createdAt");
        assertThat(UserResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("password", "passwordHash");
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}
