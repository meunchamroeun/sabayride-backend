package com.sabayride.identity.web;

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
        String p1 = rawPhone.trim();
        String p2 = p1.startsWith("+855") ? "0" + p1.substring(4) : (p1.startsWith("0") ? "+855" + p1.substring(1) : "+855" + p1);

        userRepository.findByPhone(p1).ifPresent(userRepository::delete);
        userRepository.findByPhone(p2).ifPresent(userRepository::delete);

        otpCodeRepository.deleteByPhone(p1);
        otpCodeRepository.deleteByPhone(p2);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User and OTP history for " + rawPhone + " deleted successfully.",
                "deletedPhones", java.util.List.of(p1, p2)
        ));
    }
}


