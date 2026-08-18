package com.starsoft.voint.question;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.crm.CallTranscript;
import com.starsoft.voint.crm.CallTranscriptRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Təhlilin nəticəsini bazaya yazan AYRICA bean.
 *
 * <p>Nə üçün ayrıca: bu layihədə iki dəfə eyni tələyə düşülüb. Birincisi — {@code @Transactional}
 * metodun İÇİNDƏ {@code try/catch} işə yaramır: uğursuz insert tranzaksiyanı rollback-only
 * işarələyir, Hibernate flush-u sona saxlayır və exception catch-dən SONRA, commit anında partlayır.
 * İkincisi — eyni sinifdən çağırılan {@code @Transactional} metod proxy-ni atlayır, yəni tranzaksiya
 * heç başlamır. Hər ikisinin həlli budur: yazma işi öz bean-ində, öz tranzaksiyasında.
 *
 * <p>{@code REQUIRES_NEW}: təhlil arxa planda, zəngin öz tranzaksiyası artıq commit olduqdan sonra
 * işləyir — burada bir problem çıxsa zəng qeydi zədələnməməlidir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallAnalysisWriter {

    private final UnansweredQuestionRepository questionRepository;
    private final CallTranscriptRepository transcriptRepository;

    /**
     * Tapılan sualları yazır və zəngi "təhlil olunub" kimi işarələyir.
     *
     * <p>Sual tapılmasa da işarələmə edilir: "təhlil olundu, boşluq yoxdur" ilə "heç baxılmayıb"
     * fərqli vəziyyətlərdir və geriyə dönük doldurma yalnız ikincisinə toxunmalıdır.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(UUID tenantId, UUID callId, String summary, String cleanedTranscript,
                     List<CallAnalysisService.FoundQuestion> found) {
        for (CallAnalysisService.FoundQuestion q : found) {
            questionRepository.save(UnansweredQuestion.builder()
                    .tenantId(tenantId)
                    .callId(callId)
                    .question(q.question())
                    .context(q.context())
                    .status(QuestionStatus.OPEN)
                    .build());
        }

        CallTranscript transcript = transcriptRepository.findByCallId(callId).orElse(null);
        if (transcript == null) {
            log.warn("Call {} has no transcript row to mark as analyzed", callId);
            return;
        }
        // Vapi öz xülasəsini göndərir; bizimki cavabsız sualı da adlandırdığı üçün operator üçün
        // daha faydalıdır. Vapi heç nə göndərməyibsə boşluğu doldururuq, göndəribsə üstündən
        // yazırıq - iki xülasəni yan-yana saxlamaq operatoru hansına baxacağını seçməyə məcbur edir.
        if (summary != null && !summary.isBlank()) {
            transcript.setAiSummary(summary);
        }
        if (cleanedTranscript != null && !cleanedTranscript.isBlank()) {
            transcript.setCleanedTranscript(cleanedTranscript);
        }
        transcript.setAnalyzedAt(Instant.now());
        transcriptRepository.save(transcript);
    }

    /** Təhlil cəhd edildi, amma nəticə alınmadı — yenidən cəhd oluna bilsin deyə işarələnmir. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID callId, String reason) {
        log.warn("Call analysis produced nothing for call {}: {}", callId, reason);
    }
}
