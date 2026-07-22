package com.starsoft.voint.crm.dto;

/** PATCH semantics - null fields are left unchanged. */
public record CustomerUpdateRequest(
        String phoneNumber,
        String name,
        String notes
) {
}
