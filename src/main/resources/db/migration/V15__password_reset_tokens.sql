-- "Şifrəmi unutdum" axını üçün birdəfəlik token-lər.
--
-- Token-in ÖZÜ deyil, SHA-256 hash-i saxlanılır. Səbəb: bu cədvəl oğurlansa (backup, SQL
-- injection, oxuma icazəsi olan hesab) xam token hər kəsin şifrəsini dəyişməyə imkan verərdi.
-- Hash-dən token geri qaytarmaq mümkün deyil; biz gələn token-i hash-ləyib müqayisə edirik -
-- eynilə şifrənin özü kimi.
--
-- Token e-poçtla link içində gedir. Şifrə YALNIZ link açılıb yeni şifrə təyin ediləndə dəyişir;
-- ona qədər hesabın köhnə şifrəsi toxunulmaz qalır. Bu, "generasiya et və e-poçtla göndər"
-- yanaşmasının problemini həll edir: orada kimsə başqasının e-poçtunu bilməklə onun şifrəsini
-- sıfırlayıb hesabından çıxara bilərdi.

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES panel_users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    -- Bir dəfə istifadə olunandan sonra bağlanır: eyni link ikinci dəfə işləməməlidir.
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Gələn token-i tapmaq üçün.
CREATE INDEX idx_reset_tokens_hash ON password_reset_tokens (token_hash);
-- Bir istifadəçi yeni token istəyəndə köhnələrini ləğv etmək üçün.
CREATE INDEX idx_reset_tokens_user ON password_reset_tokens (user_id);
