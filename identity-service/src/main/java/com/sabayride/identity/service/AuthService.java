package com.sabayride.identity.service;

import com.sabayride.identity.common.ApiException;
import com.sabayride.identity.domain.AuthIdentity;
import com.sabayride.identity.domain.RefreshToken;
import com.sabayride.identity.domain.User;
import com.sabayride.identity.domain.UserRoleEntity;
import com.sabayride.identity.dto.AuthTokensResponse;
import com.sabayride.identity.dto.LoginRequest;
import com.sabayride.identity.dto.MeResponse;
import com.sabayride.identity.dto.RegisterRequest;
import com.sabayride.identity.jwt.JwtService;
import com.sabayride.identity.repo.AuthIdentityRepository;
import com.sabayride.identity.repo.RefreshTokenRepository;
import com.sabayride.identity.repo.UserRepository;
import com.sabayride.identity.repo.UserRoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Register / login / refresh — issuing RS256 access tokens + opaque refresh tokens. */
@Service
public class AuthService {

    private static final String PROVIDER_PASSWORD = "PASSWORD";
    private static final String ROLE_CUSTOMER = "CUSTOMER";
    private static final int REFRESH_DAYS = 30;

    private final UserRepository users;
    private final AuthIdentityRepository identities;
    private final UserRoleRepository userRoles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, AuthIdentityRepository identities,
                       UserRoleRepository userRoles, RefreshTokenRepository refreshTokens,
                       PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.identities = identities;
        this.userRoles = userRoles;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthTokensResponse register(RegisterRequest req) {
        String phone = req.phone().trim();
        String email = (req.email() == null || req.email().isBlank())
                ? null : req.email().trim().toLowerCase();

        if (users.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_ALREADY_REGISTERED",
                    "That phone number already has an account.", "phone");
        }
        if (email != null && users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "That email already has an account.", "email");
        }

        User u = new User();
        u.setFullName(req.fullName().trim());
        u.setPhone(phone);
        u.setEmail(email);
        u.setPreferredLanguage(normaliseLang(req.preferredLanguage()));
        u.setPhoneVerified(false);
        u.setEmailVerified(email != null);
        users.save(u);

        AuthIdentity ai = new AuthIdentity();
        ai.setUserId(u.getId());
        ai.setProvider(PROVIDER_PASSWORD);
        ai.setPasswordHash(encoder.encode(req.password()));
        identities.save(ai);

        UserRoleEntity role = new UserRoleEntity();
        role.setUserId(u.getId());
        role.setRole(ROLE_CUSTOMER);
        userRoles.save(role);

        return issue(u, List.of(ROLE_CUSTOMER));
    }

    @Transactional(readOnly = true)
    public AuthTokensResponse login(LoginRequest req) {
        String id = req.identifier().trim();
        Optional<User> found = id.contains("@")
                ? users.findFirstByEmailIgnoreCase(id.toLowerCase())
                : users.findByPhone(id);

        User u = found.orElseThrow(this::invalidCredentials);

        AuthIdentity ai = identities.findByUserIdAndProvider(u.getId(), PROVIDER_PASSWORD)
                .filter(x -> x.getPasswordHash() != null && encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(this::invalidCredentials);
        // ai intentionally referenced only via the check above.

        List<String> roles = rolesFor(u.getId());
        return issue(u, roles.isEmpty() ? List.of(ROLE_CUSTOMER) : roles);
    }

    @Transactional
    public AuthTokensResponse refresh(String rawToken) {
        RefreshToken rt = refreshTokens.findByTokenHash(sha256Hex(rawToken))
                .filter(x -> x.getRevokedAt() == null && x.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                        "Refresh token is invalid or expired.", null));
        User u = users.findById(rt.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                        "Refresh token is invalid or expired.", null));
        rt.setRevokedAt(Instant.now());
        refreshTokens.save(rt);
        List<String> roles = rolesFor(u.getId());
        return issue(u, roles.isEmpty() ? List.of(ROLE_CUSTOMER) : roles);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private AuthTokensResponse issue(User u, List<String> roles) {
        String access = jwt.sign(u.getId(), roles);
        String refresh = newRefreshToken(u.getId());
        return AuthTokensResponse.bearer(access, refresh, jwt.accessTokenSeconds(), toMe(u, roles));
    }

    private String newRefreshToken(UUID userId) {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setTokenHash(sha256Hex(raw));
        rt.setExpiresAt(Instant.now().plus(REFRESH_DAYS, ChronoUnit.DAYS));
        refreshTokens.save(rt);
        return raw;
    }

    private List<String> rolesFor(UUID userId) {
        return userRoles.findByUserId(userId).stream().map(UserRoleEntity::getRole).toList();
    }

    private MeResponse toMe(User u, List<String> roles) {
        return new MeResponse(u.getId(), u.getFullName(), u.getPhone(), u.getEmail(),
                roles, u.isPhoneVerified(), u.isEmailVerified(), u.getStatus());
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Phone number or password is incorrect.", null);
    }

    private static String normaliseLang(String lang) {
        return "km".equalsIgnoreCase(lang) ? "km" : "en";
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
