package com.starsoft.voint.lead.dto;

import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.lead.Lead;
import com.starsoft.voint.lead.LeadStatus;

public record LeadResponse(
        UUID id,
        String fullName,
        String company,
        String industry,
        String phone,
        String email,
        String dailyCallVolume,
        String source,
        LeadStatus status,
        String note,
        Instant createdAt,
        Instant updatedAt) {

    public static LeadResponse from(Lead lead) {
        return new LeadResponse(
                lead.getId(),
                lead.getFullName(),
                lead.getCompany(),
                lead.getIndustry(),
                lead.getPhone(),
                lead.getEmail(),
                lead.getDailyCallVolume(),
                lead.getSource(),
                lead.getStatus(),
                lead.getNote(),
                lead.getCreatedAt(),
                lead.getUpdatedAt());
    }
}
