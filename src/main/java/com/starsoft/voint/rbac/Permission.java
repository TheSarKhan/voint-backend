package com.starsoft.voint.rbac;

import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What the permission matrix is allowed to contain.
 *
 * <p>Deliberately an enum rather than free text an operator can invent. A permission only means
 * something if an endpoint enforces it - a stored row saying {@code CALL:APPROVE} with no code
 * behind it is a promise the system silently fails to keep, and the operator has no way to tell.
 * Adding a capability therefore starts here, in code, next to the endpoint that will honour it.
 */
public final class Permission {

    private Permission() {
    }

    /** Things a user can act on. Labels are what the matrix screen shows. */
    @Getter
    @RequiredArgsConstructor
    public enum Resource {
        // Platform-level: only ever granted to platform staff.
        TENANT("Müəssisələr", true),
        LEAD("Sorğular", true),
        PROVIDER("Provayder açarları", true),

        // Present on both sides - a tenant manages its own, the platform manages everyone's.
        USER("İstifadəçilər", false),
        ROLE("Rollar", false),
        CALL("Zənglər", false),
        CUSTOMER("Müştərilər", false),
        RESERVATION("Rezervasiyalar", false),
        RAG("Bilik bazası", false),
        BILLING("Hesablaşma", false),
        SETTINGS("Ayarlar", false),

        /**
         * The approval queue. READ shows it; UPDATE decides on the entries in it - there is no
         * CREATE, because nobody creates an approval request on purpose: it is what a held
         * operation turns into.
         */
        APPROVAL("Təsdiqlər", false);

        private final String label;
        /** True when this resource has no meaning inside a single business. */
        private final boolean platformOnly;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Action {
        READ("Oxu"),
        CREATE("Yarat"),
        UPDATE("Dəyiş"),
        DELETE("Sil");

        private final String label;
    }

    /** Resources a tenant role may be granted; the matrix screen hides the rest for tenants. */
    public static List<Resource> tenantResources() {
        return List.of(Resource.values()).stream().filter(r -> !r.isPlatformOnly()).toList();
    }
}
