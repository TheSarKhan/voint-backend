package com.starsoft.voint.rbac;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    /** Roles belonging to one business. */
    List<Role> findByTenantIdOrderByName(UUID tenantId);

    /** Platform roles and templates. */
    List<Role> findByTenantIdIsNullOrderByName();

    List<Role> findByTemplateTrueOrderByName();

    Optional<Role> findByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
}
