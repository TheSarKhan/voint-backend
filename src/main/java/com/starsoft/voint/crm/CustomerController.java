package com.starsoft.voint.crm;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.call.CallRepository;
import com.starsoft.voint.crm.dto.CustomerCreateRequest;
import com.starsoft.voint.crm.dto.CustomerResponse;
import com.starsoft.voint.crm.dto.CustomerUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tenants/{id}/customers")
@RequiredArgsConstructor
@Tag(name = "CRM", description = "Customer cards (repeat callers by phone number)")
public class CustomerController {

    private final CustomerService customerService;
    private final CallRepository callRepository;
    private final TenantAccessGuard tenantAccessGuard;

    @GetMapping
    @Operation(summary = "List customers of the tenant")
    public List<CustomerResponse> list(@PathVariable("id") UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return customerService.list(tenantId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get a customer card")
    public CustomerResponse get(@PathVariable("id") UUID tenantId, @PathVariable UUID customerId) {
        tenantAccessGuard.requireAccess(tenantId);
        return toResponse(customerService.get(tenantId, customerId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a customer card")
    public CustomerResponse create(@PathVariable("id") UUID tenantId,
                                   @Valid @RequestBody CustomerCreateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return toResponse(customerService.create(tenantId, request));
    }

    @PatchMapping("/{customerId}")
    @Operation(summary = "Partially update a customer card")
    public CustomerResponse update(@PathVariable("id") UUID tenantId,
                                   @PathVariable UUID customerId,
                                   @RequestBody CustomerUpdateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return toResponse(customerService.update(tenantId, customerId, request));
    }

    /** callCount is computed on the fly (bootstrap data scale - no batching/joins needed yet). */
    private CustomerResponse toResponse(Customer customer) {
        long callCount = callRepository.countByTenantIdAndCallerNumber(customer.getTenantId(), customer.getPhoneNumber());
        return CustomerResponse.from(customer, callCount);
    }
}
