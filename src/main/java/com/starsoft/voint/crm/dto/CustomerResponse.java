package com.starsoft.voint.crm.dto;

import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.crm.Customer;

public record CustomerResponse(
        UUID id,
        UUID tenantId,
        String phoneNumber,
        String name,
        String notes,
        Instant firstSeenAt,
        Instant lastSeenAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getTenantId(), c.getPhoneNumber(), c.getName(),
                c.getNotes(), c.getFirstSeenAt(), c.getLastSeenAt());
    }
}
