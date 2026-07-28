package com.starsoft.voint.rbac;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.rbac.dto.DepartmentDetail;
import com.starsoft.voint.rbac.dto.DepartmentUpsertRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<DepartmentDetail> list(UUID tenantId) {
        List<Department> departments = tenantId == null
                ? departmentRepository.findByTenantIdIsNullOrderByName()
                : departmentRepository.findByTenantIdOrderByName(tenantId);
        return departments.stream().map(this::toDetail).toList();
    }

    @Transactional
    public DepartmentDetail create(DepartmentUpsertRequest request) {
        return toDetail(departmentRepository.save(Department.builder()
                .tenantId(request.tenantId())
                .name(request.name().trim())
                .description(request.description())
                .build()));
    }

    @Transactional
    public DepartmentDetail update(UUID id, DepartmentUpsertRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Departament", id));
        department.setName(request.name().trim());
        department.setDescription(request.description());
        return toDetail(departmentRepository.save(department));
    }

    /**
     * Deleting a department does NOT delete its roles - they simply become ungrouped.
     * A department is a way of arranging roles on a screen; the permissions people depend on
     * should not disappear because someone reorganised the folders.
     */
    @Transactional
    public void delete(UUID id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Departament", id));
        roleRepository.findAll().stream()
                .filter(r -> id.equals(r.getDepartmentId()))
                .forEach(r -> {
                    r.setDepartmentId(null);
                    roleRepository.save(r);
                });
        departmentRepository.delete(department);
    }

    private DepartmentDetail toDetail(Department d) {
        long roles = roleRepository.countByDepartmentId(d.getId());
        return new DepartmentDetail(d.getId(), d.getTenantId(), d.getName(),
                d.getDescription(), roles);
    }
}
