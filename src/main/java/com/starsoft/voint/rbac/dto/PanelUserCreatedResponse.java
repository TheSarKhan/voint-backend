package com.starsoft.voint.rbac.dto;

/**
 * Returned once, immediately after creating an account or resetting its password.
 *
 * <p>The password is shown here and nowhere else - it is hashed on the way into the database and
 * cannot be recovered afterwards. The screen has to make that clear, because an operator who
 * closes the dialog without copying it has to reset again.
 */
public record PanelUserCreatedResponse(
        PanelUserResponse user,
        /** Null when the credentials were emailed - there is then nothing to show or copy. */
        String password,
        boolean emailed
) {
}
