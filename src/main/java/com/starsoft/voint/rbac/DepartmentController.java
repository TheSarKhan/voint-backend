package com.starsoft.voint.rbac;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.rbac.dto.DepartmentDetail;
import com.starsoft.voint.rbac.dto.DepartmentUpsertRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Departments group roles. Platform departments (Satış, Dəstək) describe how Voint itself is
 * organised; a tenant's departments describe its own staff.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Groups that roles belong to")
public class DepartmentController {

    private final DepartmentService departmentService;

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.READ,
            tenantScoped = false)
    @GetMapping("/api/v1/admin/departments")
    @Operation(summary = "Departments, with how many roles each holds")
    public List<DepartmentDetail> list(@RequestParam(required = false) UUID tenantId) {
        return departmentService.list(tenantId);
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.CREATE,
            tenantScoped = false)
    @PostMapping("/api/v1/admin/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a department")
    public DepartmentDetail create(@Valid @RequestBody DepartmentUpsertRequest request) {
        return departmentService.create(request);
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.UPDATE,
            tenantScoped = false)
    @PutMapping("/api/v1/admin/departments/{id}")
    @Operation(summary = "Rename a department")
    public DepartmentDetail update(@PathVariable UUID id,
                                   @Valid @RequestBody DepartmentUpsertRequest request) {
        return departmentService.update(id, request);
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.DELETE,
            tenantScoped = false)
    @DeleteMapping("/api/v1/admin/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a department; its roles are kept and become ungrouped")
    public void delete(@PathVariable UUID id) {
        departmentService.delete(id);
    }
}
