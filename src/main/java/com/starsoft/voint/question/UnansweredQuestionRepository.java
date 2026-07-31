package com.starsoft.voint.question;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnansweredQuestionRepository extends JpaRepository<UnansweredQuestion, UUID> {

    List<UnansweredQuestion> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<UnansweredQuestion> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, QuestionStatus status);

    List<UnansweredQuestion> findByCallIdOrderByCreatedAtAsc(UUID callId);

    Optional<UnansweredQuestion> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Zəng siyahısında işarələmə üçün: hansı zənglərdə neçə AÇIQ sual var.
     * Zəng başına ayrıca sorğu vermək N+1 deməkdir — siyahı 27 zəngdə 27 sorğu.
     */
    @Query("SELECT q.callId, COUNT(q) FROM UnansweredQuestion q "
            + "WHERE q.tenantId = :tenantId AND q.status = com.starsoft.voint.question.QuestionStatus.OPEN "
            + "GROUP BY q.callId")
    List<Object[]> countOpenByCall(@Param("tenantId") UUID tenantId);
}
