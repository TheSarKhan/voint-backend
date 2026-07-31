package com.starsoft.voint.question;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.auth.TenantAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Platforma əməliyyatları: köhnə zəngləri təhlildən keçirmək. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Unanswered questions", description = "Knowledge-base gaps found by post-call analysis")
public class QuestionAdminController {

    private final CallAnalysisBackfillService backfillService;
    private final TenantAccessGuard tenantAccessGuard;

    @PostMapping("/api/v1/admin/questions/backfill")
    @Operation(summary = "Analyse calls recorded before the analysis existed (platform staff only)")
    public Map<String, Integer> backfill(@RequestParam(defaultValue = "25") int limit) {
        tenantAccessGuard.requireSuperAdmin();
        return Map.of("queued", backfillService.backfill(Math.max(1, Math.min(limit, 200))));
    }
}
