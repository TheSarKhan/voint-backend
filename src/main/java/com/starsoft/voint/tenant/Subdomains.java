package com.starsoft.voint.tenant;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rules for what may become a tenant's panel address.
 *
 * <p>A subdomain is part of a hostname, so it has to survive DNS and TLS as well as our own code:
 * lowercase, no underscores, no leading or trailing hyphen, 63 characters at most.
 */
public final class Subdomains {

    private static final Pattern VALID = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

    /**
     * Names that must never belong to a tenant, because something else already answers there or
     * will need to. Handing "admin" to a customer would point their staff at the platform console.
     */
    private static final Set<String> RESERVED = Set.of(
            "admin", "admin-panel", "api", "www", "app", "panel", "mail", "smtp", "imap",
            "ftp", "ns", "ns1", "ns2", "dns", "cdn", "static", "assets", "status", "help",
            "support", "docs", "blog", "dev", "test", "staging", "demo", "voint", "internal");

    private Subdomains() {
    }

    /** @return the cleaned subdomain, ready to store */
    public static String normalizeOrThrow(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Subdomain boş ola bilməz");
        }
        String value = raw.trim().toLowerCase();

        if (value.length() < 2) {
            throw new IllegalArgumentException("Subdomain ən azı iki simvol olmalıdır");
        }
        if (value.length() > 63) {
            throw new IllegalArgumentException("Subdomain altmış üç simvoldan uzun ola bilməz");
        }
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Subdomain yalnız kiçik latın hərfləri, rəqəm və defis ola bilər, "
                            + "defislə başlaya və ya bitə bilməz: " + raw);
        }
        if (RESERVED.contains(value)) {
            throw new IllegalArgumentException("Bu ad platforma üçün ayrılıb: " + value);
        }
        return value;
    }

    /**
     * Pulls the tenant label out of a hostname, e.g. {@code ces.voint.az -> ces}.
     *
     * @return null for a bare domain, an address with no tenant label, or a reserved name
     */
    public static String fromHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String clean = host.trim().toLowerCase();
        int colon = clean.indexOf(':');
        if (colon > -1) {
            clean = clean.substring(0, colon);
        }
        String[] labels = clean.split("\\.");
        // Needs at least label.domain.tld to carry a tenant.
        if (labels.length < 3) {
            return null;
        }
        String first = labels[0];
        return RESERVED.contains(first) ? null : first;
    }
}
