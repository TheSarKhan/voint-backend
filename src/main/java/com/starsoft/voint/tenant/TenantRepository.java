package com.starsoft.voint.tenant;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByPhoneNumber(String phoneNumber);

    /** Hostnames are case-insensitive, so the lookup must be too. */
    Optional<Tenant> findBySubdomainIgnoreCase(String subdomain);
}
