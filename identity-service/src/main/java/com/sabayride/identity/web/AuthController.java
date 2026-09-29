package com.sabayride.identity.web;

import com.sabayride.identity.domain.User;
import com.sabayride.identity.dto.*;
import com.sabayride.identity.jwt.JwtService;
import com.sabayride.identity.service.AuthService;
import com.sabayride.identity.service.OtpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Auth endpoints. Path carries the /api/v1 prefix (like rental-service), because
 * the gateway forwards /api/v1/** without stripping it.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final com.sabayride.identity.repo.UserRepository userRepository;
    private final com.sabayride.identity.repo.OtpCodeRepository otpCodeRepository;

    public AuthController(
            AuthService service,
            OtpService otpService,
            JwtService jwtService,
            com.sabayride.identity.repo.UserRepository userRepository,
            com.sabayride.identity.repo.OtpCodeRepository otpCodeRepository) {
        this.service = service;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.otpCodeRepository = otpCodeRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthTokensResponse register(@Valid @RequestBody RegisterRequest req) {
        AuthTokensResponse res = service.register(req);
        // Automatically trigger OTP for the new phone number
        try {
            otpService.sendOtp(req.phone(), "PHONE_VERIFICATION");
        } catch (Exception ignored) {
        }
        return res;
    }

    @PostMapping("/login")
    AuthTokensResponse login(@Valid @RequestBody LoginRequest req) {
        return service.login(req);
    }

    @PostMapping("/refresh")
    AuthTokensResponse refresh(@RequestBody Map<String, String> body) {
        return service.refresh(body.getOrDefault("refreshToken", ""));
    }

    /** Send SMS OTP code to phone number */
    @PostMapping("/otp/send")
    ResponseEntity<OtpResponse> sendOtp(@Valid @RequestBody OtpSendRequest req) {
        return ResponseEntity.ok(otpService.sendOtp(req.phone(), req.resolvedPurpose()));
    }

    /** Resend SMS OTP code (alias for send) */
    @PostMapping("/otp/resend")
    ResponseEntity<OtpResponse> resendOtp(@Valid @RequestBody OtpSendRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(otpService.sendOtp(req.phone(), req.resolvedPurpose()));
    }

    /** Verify 6-digit phone verification code */
    @PostMapping("/verify-phone")
    ResponseEntity<OtpResponse> verifyPhone(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody VerifyPhoneRequest req) {
        UUID userId = jwtService.parseUserId(authHeader);
        return ResponseEntity.ok(otpService.verifyPhone(req.phone(), req.code(), userId));
    }

    /** Start a password reset (Step 1 of 3: Sends 6-digit code via SMS or Email) */
    @PostMapping("/password/forgot")
    public ResponseEntity<Map<String, Object>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        String target = req.resolvedIdentifier();
        boolean isEmail = target.contains("@");
        boolean userExists = false;

        if (isEmail) {
            userExists = userRepository.findFirstByEmailIgnoreCase(target.toLowerCase()).isPresent();
        } else {
            String p1 = target.trim();
            String p2 = p1.startsWith("0") ? "+855" + p1.substring(1) : p1;
            String p3 = p1.startsWith("+855") ? "0" + p1.substring(4) : p1;
            userExists = userRepository.findByPhone(p1).isPresent()
                    || userRepository.findByPhone(p2).isPresent()
                    || userRepository.findByPhone(p3).isPresent();
        }

        if (userExists) {
            try {
                otpService.sendOtp(target, "PASSWORD_RESET");
            } catch (Exception ignored) {
            }
        }

        // OpenAPI spec contract: Always return 202 whether target exists or not
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "message", isEmail ? "If that email has an account, we've sent a code." : "If that number has an account, we've sent a code.",
                "expiresInSeconds", 600
        ));
    }

    /** Exchange reset code for a short-lived reset token (Step 2 of 3) */
    @PostMapping("/password/verify-code")
    public ResponseEntity<ResetCodeVerifiedResponse> verifyPasswordResetCode(@Valid @RequestBody VerifyResetCodeRequest req) {
        String target = req.resolvedIdentifier();
        boolean isEmail = target.contains("@");

        otpService.verifyPasswordResetCode(target, req.code());

        User user;
        if (isEmail) {
            user = userRepository.findFirstByEmailIgnoreCase(target.toLowerCase())
                    .orElseThrow(() -> new com.sabayride.identity.common.ApiException(
                            HttpStatus.BAD_REQUEST, "INVALID_RESET_CODE",
                            "That code is not valid. Request a new one.", "code"));
        } else {
            String p1 = target.trim();
            String p2 = p1.startsWith("0") ? "+855" + p1.substring(1) : p1;
            String p3 = p1.startsWith("+855") ? "0" + p1.substring(4) : p1;

            user = userRepository.findByPhone(p1)
                    .or(() -> userRepository.findByPhone(p2))
                    .or(() -> userRepository.findByPhone(p3))
                    .orElseThrow(() -> new com.sabayride.identity.common.ApiException(
                            HttpStatus.BAD_REQUEST, "INVALID_RESET_CODE",
                            "That code is not valid. Request a new one.", "code"));
        }

        String resetToken = jwtService.signPasswordResetToken(user.getId());
        return ResponseEntity.ok(ResetCodeVerifiedResponse.of(resetToken, 300));
    }

    /** Set a new password using reset token (Step 3 of 3) */
    @PostMapping("/password/reset")
    public ResponseEntity<AuthTokensResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        return ResponseEntity.ok(service.resetPassword(req.resetToken(), req.newPassword()));
    }

    /** Development/testing helper: Delete a test user by phone */
    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/test/users")
    public ResponseEntity<Map<String, Object>> deleteTestUser(@RequestParam String phone) {
        return performDeleteUser(phone);
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/test/delete-user")
    public ResponseEntity<Map<String, Object>> deleteTestUserPost(@RequestBody Map<String, String> body) {
        return performDeleteUser(body.getOrDefault("phone", ""));
    }

    private ResponseEntity<Map<String, Object>> performDeleteUser(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Phone is required"));
        }
        try {
            String p1 = rawPhone.trim();
            String p2 = p1.startsWith("+855") ? "0" + p1.substring(4) : (p1.startsWith("0") ? "+855" + p1.substring(1) : "+855" + p1);

            userRepository.deleteByPhone(p1);
            userRepository.deleteByPhone(p2);

            otpCodeRepository.deleteByPhone(p1);
            otpCodeRepository.deleteByPhone(p2);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "User and OTP history for " + rawPhone + " deleted successfully.",
                    "deletedPhones", java.util.List.of(p1, p2)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to delete user: " + e.getMessage()
            ));
        }
    }
}


