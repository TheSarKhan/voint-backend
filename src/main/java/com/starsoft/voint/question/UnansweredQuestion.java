package com.starsoft.voint.question;

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

/** Bir zəngdə agentin cavablaya bilmədiyi bir sual. Bax: V13__unanswered_questions.sql */
@Entity
@Table(name = "unanswered_questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnansweredQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "call_id", nullable = false)
    private UUID callId;

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "context", columnDefinition = "text")
    private String context;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private QuestionStatus status = QuestionStatus.OPEN;

    @Column(name = "rag_document_id")
    private UUID ragDocumentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;
}
