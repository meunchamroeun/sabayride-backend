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

    public AuthController(AuthService service, OtpService otpService, JwtService jwtService) {
        this.service = service;
        this.otpService = otpService;
        this.jwtService = jwtService;
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
}

