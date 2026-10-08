package rw.rra.roomiq.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.identity.domain.dto.AuthTokenResponse;
import rw.rra.roomiq.identity.domain.dto.LoginRequest;
import rw.rra.roomiq.identity.domain.dto.LogoutRequest;
import rw.rra.roomiq.identity.domain.dto.RefreshTokenRequest;
import rw.rra.roomiq.identity.domain.service.AuthenticationService;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Public credential and refresh-token lifecycle operations")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request validation failed"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Credentials or refresh token were not accepted")
})
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Verify active-account credentials and issue a bearer access token and refresh token.")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                 HttpServletRequest servletRequest) {
        AuthTokenResponse response = authenticationService.login(request,
                servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
        return ApiResponse.success("Authentication successful", response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens", description = "Rotate a valid refresh token and issue a new access-token pair.")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                   HttpServletRequest servletRequest) {
        AuthTokenResponse response = authenticationService.refresh(request,
                servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
        return ApiResponse.success("Tokens refreshed", response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out", description = "Revoke the supplied refresh session and invalidate its access token.")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authenticationService.logout(request);
        return ApiResponse.success("Logged out", null);
    }
}
