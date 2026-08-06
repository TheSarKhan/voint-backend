package com.starsoft.voint.passwordreset;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Bir istifadəçi yeni sıfırlama istəyəndə əvvəlki AÇIQ token-lərini ləğv edir.
     * Beləcə eyni anda yalnız bir link işlək qalır — köhnə e-poçtdakı link ölür.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = CURRENT_TIMESTAMP "
            + "WHERE t.userId = :userId AND t.usedAt IS NULL")
    void invalidateOpenTokens(@Param("userId") UUID userId);
}
