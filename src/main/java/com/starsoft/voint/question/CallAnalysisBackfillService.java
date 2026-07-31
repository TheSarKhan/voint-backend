package com.starsoft.voint.question;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.call.Call;
import com.starsoft.voint.call.CallRepository;
import com.starsoft.voint.crm.CallTranscript;
import com.starsoft.voint.crm.CallTranscriptRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Təhlildən əvvəl yazılmış zəngləri sonradan təhlildən keçirir.
 *
 * <p>Nə üçün avtomatik başlanğıcda deyil, əl ilə: hər sətir bir Gemini çağırışıdır. Tətbiq hər
 * açılanda bunu özbaşına etsə, uğursuz bir deploy dövrü xərci təkrar-təkrar ödəyər. Kim və nə vaxt
 * ödəyəcəyinə qərar vermək platforma işçisinin işidir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallAnalysisBackfillService {

    private final CallTranscriptRepository transcriptRepository;
    private final CallRepository callRepository;
    private final CallAnalysisService analysisService;

    /**
     * @param limit bir dəfəyə neçə zəng — hamısını birdən növbəyə yığmaq icraçının növbəsini
     *              doldurur və CallerRunsPolicy sorğunu özünü gözlədən uzun əməliyyata çevirir
     * @return növbəyə salınan zəng sayı
     */
    @Transactional(readOnly = true)
    public int backfill(int limit) {
        List<CallTranscript> pending = transcriptRepository.findUnanalyzed(Limit.of(limit));
        int queued = 0;
        for (CallTranscript transcript : pending) {
            Call call = callRepository.findById(transcript.getCallId()).orElse(null);
            if (call == null) {
                log.warn("Transcript {} points at a missing call {}", transcript.getId(), transcript.getCallId());
                continue;
            }
            analysisService.analyzeAsync(call.getTenantId(), call.getId(), transcript.getFullTranscript());
            queued++;
        }
        log.info("Queued {} call(s) for backfill analysis", queued);
        return queued;
    }
}
