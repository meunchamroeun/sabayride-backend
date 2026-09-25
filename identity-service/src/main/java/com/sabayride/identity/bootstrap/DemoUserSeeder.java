package com.sabayride.identity.bootstrap;

import com.sabayride.identity.domain.AuthIdentity;
import com.sabayride.identity.domain.User;
import com.sabayride.identity.domain.UserRoleEntity;
import com.sabayride.identity.repo.AuthIdentityRepository;
import com.sabayride.identity.repo.UserRepository;
import com.sabayride.identity.repo.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ensures a deterministic account per app role exists so an app-store reviewer
 * can sign in against the REAL backend and reach every screen. Idempotent:
 * each account is only inserted when its email is absent.
 * Disable with sabayride.seed.demo-user=false.
 *
 * NOTE: identity only issues CUSTOMER / ADMIN per the original schema; SHOP_OWNER
 * is granted here (and allowed by V2) purely so a demo account can surface the
 * shop role to the client. Real shop ownership is scoped to rental.shop_member.
 */
@Component
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);
    private static final String PASSWORD = "password123";

    private final UserRepository users;
    private final AuthIdentityRepository identities;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder encoder;
    private final boolean enabled;

    public DemoUserSeeder(UserRepository users, AuthIdentityRepository identities,
                          UserRoleRepository userRoles, PasswordEncoder encoder,
                          @Value("${sabayride.seed.demo-user:true}") boolean enabled) {
        this.users = users;
        this.identities = identities;
        this.userRoles = userRoles;
        this.encoder = encoder;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        seed("Customer Traveller", "customer@sabayride.app", "+85512000001", List.of("CUSTOMER"));
        seed("Angkor Moto Rent (Owner)", "shopowner@sabayride.app", "+85512000002",
                List.of("CUSTOMER", "SHOP_OWNER"));
        seed("SabayRide Admin", "admin@sabayride.app", "+85512000003",
                List.of("CUSTOMER", "ADMIN"));
    }

    private void seed(String fullName, String email, String phone, List<String> roles) {
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        User u = new User();
        u.setFullName(fullName);
        u.setEmail(email);
        u.setPhone(phone);
        u.setPhoneVerified(true);
        u.setEmailVerified(true);
        u.setPreferredLanguage("en");
        users.save(u);

        AuthIdentity ai = new AuthIdentity();
        ai.setUserId(u.getId());
        ai.setProvider("PASSWORD");
        ai.setPasswordHash(encoder.encode(PASSWORD));
        identities.save(ai);

        for (String role : roles) {
            UserRoleEntity r = new UserRoleEntity();
            r.setUserId(u.getId());
            r.setRole(role);
            userRoles.save(r);
        }
        log.info("Seeded demo login: {} {} (roles={})", email, PASSWORD, roles);
    }
}
