package com.starsoft.voint.outbound;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundDispatcherService {

    private final OutboundCampaignRepository campaignRepository;
    private final OutboundContactRepository contactRepository;

    /**
     * Dispatcher ticks periodically to check running campaigns and initiate outbound calls.
     */
    @Scheduled(fixedDelay = 15000)
    public void dispatchNextCalls() {
        List<OutboundCampaign> activeCampaigns = campaignRepository.findByStatus("RUNNING");
        if (activeCampaigns.isEmpty()) return;

        LocalTime now = LocalTime.now();

        for (OutboundCampaign campaign : activeCampaigns) {
            try {
                if (!isWithinCallingHours(campaign, now)) {
                    log.debug("Campaign {} is outside calling hours ({}-{})",
                            campaign.getName(), campaign.getCallingHoursStart(), campaign.getCallingHoursEnd());
                    continue;
                }

                dispatchForCampaign(campaign);
            } catch (Exception e) {
                log.error("Error dispatching calls for campaign {}", campaign.getId(), e);
            }
        }
    }

    @Transactional
    public void dispatchForCampaign(OutboundCampaign campaign) {
        int limit = Math.max(1, campaign.getConcurrencyLimit());
        List<OutboundContact> batch = contactRepository.findNextDialableContacts(
                campaign.getId(),
                campaign.getMaxRetries(),
                Instant.now(),
                PageRequest.of(0, limit)
        );

        if (batch.isEmpty()) {
            return;
        }

        for (OutboundContact contact : batch) {
            log.info("Initiating outbound AI call for campaign '{}' -> {} ({})",
                    campaign.getName(), contact.getCustomerName(), contact.getPhoneNumber());

            contact.setStatus("DIALING");
            contact.setLastAttemptAt(Instant.now());
            contact.setRetryCount(contact.getRetryCount() + 1);
            contactRepository.save(contact);

            // In local/production telephony: trigger Asterisk AMI Originate or Vapi Outbound API
            // For now, simulator/dispatcher marks call initiated.
        }
    }

    private boolean isWithinCallingHours(OutboundCampaign campaign, LocalTime now) {
        try {
            LocalTime start = LocalTime.parse(campaign.getCallingHoursStart());
            LocalTime end = LocalTime.parse(campaign.getCallingHoursEnd());
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (Exception e) {
            return true; // fallback to true if invalid format
        }
    }
}
