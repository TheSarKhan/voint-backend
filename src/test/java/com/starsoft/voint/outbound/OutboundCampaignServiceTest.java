package com.starsoft.voint.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.outbound.dto.OutboundCampaignCreateRequest;
import com.starsoft.voint.outbound.dto.OutboundContactAddRequest;

@SpringBootTest
@Transactional
class OutboundCampaignServiceTest {

    @Autowired
    private OutboundCampaignService campaignService;

    @Autowired
    private OutboundCampaignRepository campaignRepository;

    @Autowired
    private OutboundContactRepository contactRepository;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        contactRepository.deleteAll();
        campaignRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create outbound sales campaign, add contacts, start and process call outcomes")
    void testCampaignWorkflow() {
        // 1. Create campaign
        OutboundCampaignCreateRequest req = new OutboundCampaignCreateRequest(
                "Yaz Endirimi Kampaniyası",
                "SALES_OUTBOUND",
                "Müştəriyə 20% yaz endirimi haqqında məlumat ver və maraqlanıb-maraqlanmadığını soruş.",
                "Salam! Sizə CES şirkətindən zəng edirik.",
                "10:00",
                "18:00",
                2,
                60,
                2
        );

        OutboundCampaign campaign = campaignService.createCampaign(TENANT_ID, req);
        assertThat(campaign.getId()).isNotNull();
        assertThat(campaign.getStatus()).isEqualTo("DRAFT");
        assertThat(campaign.getName()).isEqualTo("Yaz Endirimi Kampaniyası");

        // 2. Add contacts
        List<OutboundContactAddRequest> contacts = List.of(
                new OutboundContactAddRequest("0501234567", "Əli Məmmədov", "Tikinti şirkəti rəhbəri"),
                new OutboundContactAddRequest("0559876543", "Vüqar Həsənov", "Fərdi podratçı")
        );

        List<OutboundContact> added = campaignService.addContacts(TENANT_ID, campaign.getId(), contacts);
        assertThat(added).hasSize(2);
        assertThat(added.get(0).getPhoneNumber()).isEqualTo("+994501234567");

        // Verify campaign total contacts
        OutboundCampaign reloaded = campaignService.getCampaign(TENANT_ID, campaign.getId());
        assertThat(reloaded.getTotalContacts()).isEqualTo(2);

        // 3. Start campaign
        OutboundCampaign started = campaignService.startCampaign(TENANT_ID, campaign.getId());
        assertThat(started.getStatus()).isEqualTo("RUNNING");

        // 4. Update contact outcome
        OutboundContact firstContact = added.get(0);
        OutboundContact updatedContact = campaignService.updateContactStatus(
                TENANT_ID,
                firstContact.getId(),
                "ANSWERED",
                "INTERESTED",
                "Müştəri 3 günlük JCB ekskavator icarəsi ilə maraqlandı"
        );
        assertThat(updatedContact.getStatus()).isEqualTo("ANSWERED");
        assertThat(updatedContact.getCallOutcome()).isEqualTo("INTERESTED");

        // 5. Verify stats updated on campaign
        OutboundCampaign withStats = campaignService.getCampaign(TENANT_ID, campaign.getId());
        assertThat(withStats.getSuccessfulCount()).isEqualTo(1);
        assertThat(withStats.getContactedCount()).isEqualTo(1);
    }
}
