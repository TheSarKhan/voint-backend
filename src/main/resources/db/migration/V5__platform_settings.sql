-- Provider credentials, editable from the admin panel instead of by hand on the server.
--
-- Written after an ElevenLabs key was revoked and it turned out the same secret had to be
-- updated in two disconnected places (Vapi's dashboard and the server's .env), with no way to
-- tell from the product whether either was correct.
--
-- Values are encrypted (AES-GCM) before they get here: this table is backed up and read by
-- anyone with database access, and a plaintext API key in a backup is a leak waiting to happen.
-- The column holds ciphertext, never the secret itself.

CREATE TABLE platform_settings (
    setting_key VARCHAR(64) PRIMARY KEY,
    value_enc   TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Email of the SUPER_ADMIN who last changed it, so a surprise rotation has a name on it.
    updated_by  VARCHAR(255)
);
