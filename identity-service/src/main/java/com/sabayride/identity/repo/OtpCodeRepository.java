package com.sabayride.identity.repo;

import com.sabayride.identity.domain.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    @Query("SELECT o FROM OtpCode o WHERE o.phone = :phone AND o.purpose = :purpose AND o.consumedAt IS NULL ORDER BY o.createdAt DESC LIMIT 1")
    Optional<OtpCode> findLatestActive(@Param("phone") String phone, @Param("purpose") String purpose);

    @Query("SELECT COUNT(o) FROM OtpCode o WHERE o.phone = :phone AND o.createdAt >= :after")
    long countRecentRequests(@Param("phone") String phone, @Param("after") Instant after);

    void deleteByPhone(String phone);
}

