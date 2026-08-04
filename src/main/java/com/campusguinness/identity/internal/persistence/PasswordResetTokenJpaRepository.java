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

public interface PasswordResetTokenJpaRepository
        extends JpaRepository<PasswordResetTokenEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    List<PasswordResetTokenEntity> findByUserIdAndUsedAtIsNullAndExpiresAtAfter(
            UUID userId, Instant now);

    @Modifying
    @Query("""
            update PasswordResetTokenEntity t
               set t.usedAt = :usedAt
             where t.userId = :userId
               and t.usedAt is null
            """)
    int markActiveTokensUsed(@Param("userId") UUID userId,
            @Param("usedAt") Instant usedAt);
}
