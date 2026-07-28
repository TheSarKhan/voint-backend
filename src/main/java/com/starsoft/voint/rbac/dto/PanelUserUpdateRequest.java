package com.starsoft.voint.rbac.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Editing an account's own details. Role, status and password each have their own endpoint,
 * because each has a consequence this one does not: a role change alters what the person may do,
 * a status change locks them out, a password change invalidates what they have written down.
 *
 * @param email changing this changes the login itself - see PanelUserService#update
 */
public record PanelUserUpdateRequest(
        @NotBlank @Email String email,
        String fullName
) {
}
