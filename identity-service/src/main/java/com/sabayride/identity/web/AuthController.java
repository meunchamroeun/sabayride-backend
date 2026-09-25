package com.sabayride.identity.web;

import com.sabayride.identity.dto.AuthTokensResponse;
import com.sabayride.identity.dto.LoginRequest;
import com.sabayride.identity.dto.RegisterRequest;
import com.sabayride.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Auth endpoints. Path carries the /api/v1 prefix (like rental-service), because
 * the gateway forwards /api/v1/** without stripping it.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthTokensResponse register(@Valid @RequestBody RegisterRequest req) {
        return service.register(req);
    }

    @PostMapping("/login")
    AuthTokensResponse login(@Valid @RequestBody LoginRequest req) {
        return service.login(req);
    }

    @PostMapping("/refresh")
    AuthTokensResponse refresh(@RequestBody Map<String, String> body) {
        return service.refresh(body.getOrDefault("refreshToken", ""));
    }
}
