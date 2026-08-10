package com.starsoft.voint.rbac;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findByTenantIdOrderByName(UUID tenantId);

    List<Department> findByTenantIdIsNullOrderByName();

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
}
