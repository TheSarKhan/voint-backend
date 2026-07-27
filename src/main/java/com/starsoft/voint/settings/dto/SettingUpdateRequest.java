package com.starsoft.voint.settings.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param value      the new credential
 * @param syncToVapi whether to push the change into Vapi as well. Defaults to true for the
 *                   ElevenLabs keys, since leaving Vapi stale is the exact failure this feature
 *                   exists to prevent.
 */
public record SettingUpdateRequest(
        @NotBlank String value,
        Boolean syncToVapi
) {
    public boolean shouldSync() {
        return syncToVapi == null || syncToVapi;
    }
}
