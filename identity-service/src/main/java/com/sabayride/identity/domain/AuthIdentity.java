package com.sabayride.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * Maps to identity.auth_identity — one row per (user, provider).
 * For PASSWORD rows: passwordHash set, providerSubject null.
 */
@Entity
@Table(name = "auth_identity")
@Getter
@Setter
@NoArgsConstructor
public class AuthIdentity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_subject", length = 255)
    private String providerSubject;

    @Column(name = "password_hash", length = 72)
    private String passwordHash;
}
