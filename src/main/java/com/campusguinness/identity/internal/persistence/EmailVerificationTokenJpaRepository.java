package com.campusguinness.identity.internal.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenJpaRepository
        extends JpaRepository<EmailVerificationTokenEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);

    List<EmailVerificationTokenEntity> findByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfter(
            UUID userId, String purpose, Instant now);

    @Modifying
    @Query("""
            update EmailVerificationTokenEntity t
               set t.usedAt = :usedAt
             where t.userId = :userId
               and t.purpose = :purpose
               and t.usedAt is null
            """)
    int markUnusedTokensUsed(@Param("userId") UUID userId,
            @Param("purpose") String purpose,
            @Param("usedAt") Instant usedAt);
}
