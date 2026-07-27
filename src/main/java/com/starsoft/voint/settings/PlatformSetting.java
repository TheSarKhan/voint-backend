package com.starsoft.voint.settings;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One encrypted platform-wide setting. The value is ciphertext - see {@link SecretCipher}. */
@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSetting {

    @Id
    @Column(name = "setting_key", nullable = false, updatable = false)
    private String key;

    @Column(name = "value_enc", nullable = false)
    private String valueEnc;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private String updatedBy;
}
