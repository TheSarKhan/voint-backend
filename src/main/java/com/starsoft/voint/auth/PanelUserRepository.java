package com.starsoft.voint.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PanelUserRepository extends JpaRepository<PanelUser, UUID> {

    Optional<PanelUser> findByEmail(String email);

    Optional<PanelUser> findByEmailIgnoreCase(String email);

    List<PanelUser> findByTenantIdOrderByCreatedAt(UUID tenantId);

    /** Platform staff: the accounts not tied to any business. */
    List<PanelUser> findByTenantIdIsNullOrderByCreatedAt();

    boolean existsByEmailIgnoreCase(String email);

    /** Asked once per role when listing them; counting in the database beats loading every user. */
    long countByRoleId(UUID roleId);
}
