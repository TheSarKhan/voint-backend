package com.starsoft.voint.tenant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A tenant = one business using the Voint platform (e.g. CES equipment rental). */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Phone number Vapi routes to this tenant. */
    @Column(name = "phone_number")
    private String phoneNumber;

    /** First sentence the agent says when answering a call. */
    @Column(name = "greeting_text")
    private String greetingText;

    /** Free-form working hours description, e.g. "B.e-Cümə 09:00-18:00". */
    @Column(name = "working_hours")
    private String workingHours;

    /** Human operator number for handoff. */
    @Column(name = "handoff_number")
    private String handoffNumber;

    /** Language settings (JSON string for now, e.g. {"default":"az","supported":["az","ru","en"]}). */
    @Column(name = "language_config")
    private String languageConfig;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
