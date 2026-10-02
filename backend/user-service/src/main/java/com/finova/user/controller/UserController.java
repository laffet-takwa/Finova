package com.finova.user.controller;

import com.finova.common.security.CurrentUser;
import com.finova.user.dto.AuthUserResponse;
import com.finova.user.dto.ChangePasswordRequest;
import com.finova.user.dto.SecurityStatusResponse;
import com.finova.user.dto.UpdateProfileRequest;
import com.finova.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own identity.
 * <p>
 * There is no id in any path or query parameter. The subject comes from the verified
 * bearer token via {@link CurrentUser}, so there is no request a caller can craft to
 * reach somebody else's profile.
 */
@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Profile", description = "The signed-in customer's own profile and security status")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    @Operation(summary = "Read my profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's profile",
                    content = @Content(schema = @Schema(implementation = AuthUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication is required",
                    ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    ref = "#/components/responses/NotFound")
    })
    public ResponseEntity<AuthUserResponse> me() {
        return ResponseEntity.ok(userProfileService.currentProfile(CurrentUser.userId()));
    }

    @PutMapping
    @Operation(summary = "Update my profile",
            description = "Name and phone only. Email is not editable here and the role is never "
                    + "caller-controlled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated profile",
                    content = @Content(schema = @Schema(implementation = AuthUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.finova.common.error.ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Authentication is required",
                    ref = "#/components/responses/Unauthorized")
    })
    public ResponseEntity<AuthUserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userProfileService.updateProfile(CurrentUser.userId(), request));
    }

    @PostMapping("/password")
    @Operation(summary = "Change my password",
            description = "Ends every other session. Answered 204 with no body, so neither the old "
                    + "nor the new password can be echoed back.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
                    content = @Content(schema = @Schema(implementation = com.finova.common.error.ApiError.class))),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS or UNAUTHENTICATED",
                    ref = "#/components/responses/Unauthorized")
    })
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userProfileService.changePassword(CurrentUser.userId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/security")
    @Operation(summary = "Read my security status",
            description = "Second-factor flags are always false and the location is never resolved: "
                    + "this service records the address it saw and nothing more.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Security panel state",
                    content = @Content(schema = @Schema(implementation = SecurityStatusResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication is required",
                    ref = "#/components/responses/Unauthorized")
    })
    public ResponseEntity<SecurityStatusResponse> securityStatus() {
        return ResponseEntity.ok(userProfileService.securityStatus(CurrentUser.userId()));
    }
}
