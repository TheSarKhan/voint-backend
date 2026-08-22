package com.starsoft.voint.outbound;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.outbound.dto.OutboundCampaignCreateRequest;
import com.starsoft.voint.outbound.dto.OutboundCampaignUpdateRequest;
import com.starsoft.voint.outbound.dto.OutboundContactAddRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundCampaignService {

    private final OutboundCampaignRepository campaignRepository;
    private final OutboundContactRepository contactRepository;

    @Transactional
    public OutboundCampaign createCampaign(UUID tenantId, OutboundCampaignCreateRequest request) {
        OutboundCampaign campaign = OutboundCampaign.builder()
                .tenantId(tenantId)
                .name(request.name().trim())
                .campaignType(request.campaignType() != null ? request.campaignType() : "SALES_OUTBOUND")
                .status("DRAFT")
                .agentPrompt(request.agentPrompt())
                .greetingText(request.greetingText())
                .callingHoursStart(request.callingHoursStart() != null ? request.callingHoursStart() : "10:00")
                .callingHoursEnd(request.callingHoursEnd() != null ? request.callingHoursEnd() : "19:00")
                .maxRetries(request.maxRetries() != null ? request.maxRetries() : 2)
                .retryIntervalMinutes(request.retryIntervalMinutes() != null ? request.retryIntervalMinutes() : 60)
                .concurrencyLimit(request.concurrencyLimit() != null ? request.concurrencyLimit() : 1)
                .build();

        return campaignRepository.save(campaign);
    }

    @Transactional(readOnly = true)
    public List<OutboundCampaign> listCampaigns(UUID tenantId) {
        return campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public OutboundCampaign getCampaign(UUID tenantId, UUID id) {
        return campaignRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> NotFoundException.of("OutboundCampaign", id));
    }

    @Transactional
    public OutboundCampaign updateCampaign(UUID tenantId, UUID id, OutboundCampaignUpdateRequest request) {
        OutboundCampaign campaign = getCampaign(tenantId, id);

        if (request.name() != null && !request.name().isBlank()) {
            campaign.setName(request.name().trim());
        }
        if (request.campaignType() != null) campaign.setCampaignType(request.campaignType());
        if (request.status() != null) campaign.setStatus(request.status());
        if (request.agentPrompt() != null) campaign.setAgentPrompt(request.agentPrompt());
        if (request.greetingText() != null) campaign.setGreetingText(request.greetingText());
        if (request.callingHoursStart() != null) campaign.setCallingHoursStart(request.callingHoursStart());
        if (request.callingHoursEnd() != null) campaign.setCallingHoursEnd(request.callingHoursEnd());
        if (request.maxRetries() != null) campaign.setMaxRetries(request.maxRetries());
        if (request.retryIntervalMinutes() != null) campaign.setRetryIntervalMinutes(request.retryIntervalMinutes());
        if (request.concurrencyLimit() != null) campaign.setConcurrencyLimit(request.concurrencyLimit());

        campaign.setUpdatedAt(Instant.now());
        return campaignRepository.save(campaign);
    }

    @Transactional
    public void deleteCampaign(UUID tenantId, UUID id) {
        OutboundCampaign campaign = getCampaign(tenantId, id);
        campaignRepository.delete(campaign);
    }

    @Transactional
    public List<OutboundContact> addContacts(UUID tenantId, UUID campaignId, List<OutboundContactAddRequest> requests) {
        OutboundCampaign campaign = getCampaign(tenantId, campaignId);
        List<OutboundContact> saved = new ArrayList<>();

        for (OutboundContactAddRequest req : requests) {
            if (req.phoneNumber() == null || req.phoneNumber().isBlank()) continue;
            String normalizedPhone = normalizePhone(req.phoneNumber());

            OutboundContact contact = OutboundContact.builder()
                    .campaignId(campaignId)
                    .tenantId(tenantId)
                    .phoneNumber(normalizedPhone)
                    .customerName(req.customerName() != null ? req.customerName().trim() : null)
                    .customData(req.customData())
                    .status("PENDING")
                    .build();
            saved.add(contactRepository.save(contact));
        }

        recalculateStats(campaign);
        return saved;
    }

    @Transactional
    public List<OutboundContact> importContactsFromFile(UUID tenantId, UUID campaignId, MultipartFile file) {
        OutboundCampaign campaign = getCampaign(tenantId, campaignId);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        List<OutboundContactAddRequest> parsed = new ArrayList<>();

        try {
            if (filename.endsWith(".csv") || filename.endsWith(".txt")) {
                parsed = parseCsv(file);
            } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                parsed = parseExcel(file);
            } else {
                throw new IllegalArgumentException("Dəstəklənməyən fayl formatı. Yalnız .xlsx, .xls, .csv dəstəklənir.");
            }
        } catch (Exception e) {
            log.error("Failed to parse outbound contacts from file {}", filename, e);
            throw new RuntimeException("Fayl oxunarkən xəta baş verdi: " + e.getMessage(), e);
        }

        return addContacts(tenantId, campaignId, parsed);
    }

    @Transactional
    public OutboundCampaign startCampaign(UUID tenantId, UUID campaignId) {
        OutboundCampaign campaign = getCampaign(tenantId, campaignId);
        campaign.setStatus("RUNNING");
        campaign.setUpdatedAt(Instant.now());
        return campaignRepository.save(campaign);
    }

    @Transactional
    public OutboundCampaign pauseCampaign(UUID tenantId, UUID campaignId) {
        OutboundCampaign campaign = getCampaign(tenantId, campaignId);
        campaign.setStatus("PAUSED");
        campaign.setUpdatedAt(Instant.now());
        return campaignRepository.save(campaign);
    }

    @Transactional(readOnly = true)
    public Page<OutboundContact> getContacts(UUID tenantId, UUID campaignId, Pageable pageable) {
        getCampaign(tenantId, campaignId); // verify exists
        return contactRepository.findByCampaignId(campaignId, pageable);
    }

    @Transactional
    public OutboundContact updateContactStatus(UUID tenantId, UUID contactId, String status, String outcome, String notes) {
        OutboundContact contact = contactRepository.findByTenantIdAndId(tenantId, contactId)
                .orElseThrow(() -> NotFoundException.of("OutboundContact", contactId));

        if (status != null) contact.setStatus(status);
        if (outcome != null) contact.setCallOutcome(outcome);
        if (notes != null) contact.setNotes(notes);
        contact.setUpdatedAt(Instant.now());

        OutboundContact saved = contactRepository.save(contact);

        OutboundCampaign campaign = campaignRepository.findById(contact.getCampaignId()).orElse(null);
        if (campaign != null) {
            recalculateStats(campaign);
        }

        return saved;
    }

    private void recalculateStats(OutboundCampaign campaign) {
        long total = contactRepository.countByCampaignId(campaign.getId());
        long answered = contactRepository.countByCampaignIdAndStatus(campaign.getId(), "ANSWERED");
        long failed = contactRepository.countByCampaignIdAndStatus(campaign.getId(), "FAILED")
                + contactRepository.countByCampaignIdAndStatus(campaign.getId(), "DO_NOT_CALL");
        long dialed = total - contactRepository.countByCampaignIdAndStatus(campaign.getId(), "PENDING");

        campaign.setTotalContacts((int) total);
        campaign.setContactedCount((int) dialed);
        campaign.setSuccessfulCount((int) answered);
        campaign.setFailedCount((int) failed);

        if (total > 0 && dialed >= total && contactRepository.countByCampaignIdAndStatus(campaign.getId(), "PENDING") == 0) {
            if ("RUNNING".equals(campaign.getStatus())) {
                campaign.setStatus("COMPLETED");
            }
        }
        campaignRepository.save(campaign);
    }

    private List<OutboundContactAddRequest> parseCsv(MultipartFile file) throws Exception {
        List<OutboundContactAddRequest> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split("[,;\t]");
                if (first) {
                    first = false;
                    if (parts[0].toLowerCase().contains("telefon") || parts[0].toLowerCase().contains("phone") || parts[0].toLowerCase().contains("nömrə")) {
                        continue;
                    }
                }
                String phone = parts.length > 0 ? parts[0].trim() : "";
                String name = parts.length > 1 ? parts[1].trim() : null;
                String custom = parts.length > 2 ? parts[2].trim() : null;

                if (!phone.isBlank()) {
                    list.add(new OutboundContactAddRequest(phone, name, custom));
                }
            }
        }
        return list;
    }

    private List<OutboundContactAddRequest> parseExcel(MultipartFile file) throws Exception {
        List<OutboundContactAddRequest> list = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            boolean first = true;

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (row == null) continue;

                Cell c0 = row.getCell(0);
                Cell c1 = row.getCell(1);
                Cell c2 = row.getCell(2);

                String phone = getCellValue(c0);
                String name = getCellValue(c1);
                String custom = getCellValue(c2);

                if (first) {
                    first = false;
                    if (phone.toLowerCase().contains("telefon") || phone.toLowerCase().contains("phone") || phone.toLowerCase().contains("nömrə")) {
                        continue;
                    }
                }

                if (!phone.isBlank()) {
                    list.add(new OutboundContactAddRequest(phone, name.isBlank() ? null : name, custom.isBlank() ? null : custom));
                }
            }
        }
        return list;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            if (d == (long) d) return String.format("%d", (long) d);
            return String.valueOf(d);
        }
        return cell.toString().trim();
    }

    private String normalizePhone(String raw) {
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("0")) {
            return "+994" + digits.substring(1);
        }
        if (digits.startsWith("994")) {
            return "+" + digits;
        }
        if (!digits.startsWith("+") && digits.length() == 9) {
            return "+994" + digits;
        }
        return digits;
    }
}
