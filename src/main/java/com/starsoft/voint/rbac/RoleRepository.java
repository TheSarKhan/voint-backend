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

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    /** Roles filed under one department, used to refuse deleting a department still in use. */
    long countByDepartmentId(UUID departmentId);

    /** A template department's own roles - the picklist when copying the whole department. */
    List<Role> findByDepartmentIdOrderByName(UUID departmentId);
}
