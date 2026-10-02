package com.finova.user.controller;

import com.finova.user.dto.AuthResponse;
import com.finova.user.dto.LoginRequest;
import com.finova.user.dto.LogoutRequest;
import com.finova.user.dto.RefreshTokenRequest;
import com.finova.user.dto.RegisterRequest;
import com.finova.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public half of the service: everything a caller can do before they have a
 * session.
 * <p>
 * {@code @SecurityRequirements} with no name clears the global bearer requirement for
 * this controller only, so Swagger UI renders the sign-in form without demanding a
 * token the caller does not have yet.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, sign-in and session lifecycle")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a customer account",
            description = "Creates the identity and signs the caller in immediately. "
                    + "The password is hashed with BCrypt and is never echoed back.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created and signed in",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.finova.common.error.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_EXISTS",
                    content = @Content(schema = @Schema(implementation = com.finova.common.error.ApiError.class)))
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in",
            description = "Issues an access token and a refresh token. An unknown address and a "
                    + "wrong password both answer INVALID_CREDENTIALS, so this endpoint cannot be "
                    + "used to discover which addresses have an account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signed in",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS",
                    content = @Content(schema = @Schema(implementation = com.finova.common.error.ApiError.class))),
            @ApiResponse(responseCode = "403", description = "ACCOUNT_LOCKED",
                    content = @Content(schema = @Schema(implementation = com.finova.common.error.ApiError.class)))
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a session",
            description = "Exchanges a refresh token for a new pair and revokes the presented one. "
                    + "A token must carry typ=refresh: an access token is refused here.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "TOKEN_INVALID",
                    content = @Content(schema = @Schema(implementation = com.finova.common.error.ApiError.class)))
    })
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "End a session",
            description = "Revokes the presented refresh token. Always answers 204, including for an "
                    + "unknown, malformed or absent token, so the client can clear local state "
                    + "unconditionally. A bearer access token may still be sent so the audit row can "
                    + "name who ended the session, but it is not required.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Session ended (or already ended)"))
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        String refreshToken = request == null ? null : request.refreshToken();
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }
}
