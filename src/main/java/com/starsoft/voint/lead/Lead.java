package com.starsoft.voint.lead;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A pilot request from the public landing page - someone who wants Voint but is not a tenant yet.
 * Nothing here is trusted: every field arrives from an unauthenticated form.
 */
@Entity
@Table(name = "leads")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String company;

    private String industry;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String email;

    /** Free text - the form asks for a rough number and people answer "40-50". */
    @Column(name = "daily_call_volume")
    private String dailyCallVolume;

    @Column(nullable = false)
    @Builder.Default
    private String source = "landing";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LeadStatus status = LeadStatus.NEW;

    /** What the operator wrote after following up. */
    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
