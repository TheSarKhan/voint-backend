package com.starsoft.voint.call;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.call.dto.CallCreateRequest;
import com.starsoft.voint.common.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CallService {

    private final CallRepository callRepository;

    @Transactional(readOnly = true)
    public List<Call> list(UUID tenantId) {
        return callRepository.findByTenantIdOrderByStartedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public Call get(UUID tenantId, UUID callId) {
        return callRepository.findByIdAndTenantId(callId, tenantId)
                .orElseThrow(() -> NotFoundException.of("Call", callId));
    }

    @Transactional
    public Call create(UUID tenantId, CallCreateRequest request) {
        Call call = Call.builder()
                .tenantId(tenantId)
                .callerNumber(request.callerNumber())
                .languageDetected(request.languageDetected())
                .status(request.status() != null ? request.status() : CallStatus.ONGOING)
                .durationSeconds(request.durationSeconds())
                .startedAt(request.startedAt() != null ? request.startedAt() : Instant.now())
                .endedAt(request.endedAt())
                .build();
        return callRepository.save(call);
    }
}
