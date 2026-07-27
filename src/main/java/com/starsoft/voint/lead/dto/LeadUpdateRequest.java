package com.starsoft.voint.lead.dto;

import com.starsoft.voint.lead.LeadStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What an operator may change about a lead: where it stands, and what they learned. */
public record LeadUpdateRequest(

        @NotNull(message = "Status seçilməlidir")
        LeadStatus status,

        @Size(max = 4000)
        String note) {
}
