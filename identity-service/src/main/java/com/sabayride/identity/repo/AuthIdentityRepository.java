package com.sabayride.identity.repo;

import com.sabayride.identity.domain.AuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {

    Optional<AuthIdentity> findByUserIdAndProvider(UUID userId, String provider);
}
