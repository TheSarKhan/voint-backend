package com.starsoft.voint.tenant;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByPhoneNumber(String phoneNumber);

    /** Hostnames are case-insensitive, so the lookup must be too. */
    Optional<Tenant> findBySubdomainIgnoreCase(String subdomain);

    /**
     * Searches the fields the admin table actually shows. A blank query returns everything, so the
     * same method serves both the filtered and unfiltered table and there is one code path to test.
     */
    @Query("""
            select t from Tenant t
            where :q = ''
               or lower(t.name) like concat('%', :q, '%')
               or lower(coalesce(t.subdomain, '')) like concat('%', :q, '%')
               or lower(coalesce(t.phoneNumber, '')) like concat('%', :q, '%')
            """)
    Page<Tenant> search(@Param("q") String q, Pageable pageable);
}
