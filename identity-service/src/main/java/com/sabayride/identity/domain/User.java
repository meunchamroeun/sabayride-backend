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
 * Maps to identity.users (see db/migration/V1__init.sql).
 *
 * created_at / updated_at are intentionally NOT mapped: the database owns them
 * (defaults + a trigger). Hibernate runs in validate mode, so we must not send
 * NULLs for those NOT NULL columns on insert — leaving them out lets the DB
 * defaults apply.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "preferred_language", nullable = false, length = 2)
    private String preferredLanguage = "en";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";
}
