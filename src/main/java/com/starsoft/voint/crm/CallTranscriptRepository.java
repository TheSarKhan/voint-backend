package com.starsoft.voint.crm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CallTranscriptRepository extends JpaRepository<CallTranscript, UUID> {

    Optional<CallTranscript> findByCallId(UUID callId);

    /**
     * Geriyə dönük təhlil üçün: təhlildən əvvəl yazılmış, ya da Gemini əlçatmaz olduğu üçün
     * buraxılmış zənglər. Transkripti olmayan sətir götürülmür — təhlil ediləsi bir şey yoxdur.
     */
    @Query("SELECT t FROM CallTranscript t WHERE t.analyzedAt IS NULL AND t.fullTranscript IS NOT NULL "
            + "ORDER BY t.createdAt DESC")
    List<CallTranscript> findUnanalyzed(Limit limit);
}
