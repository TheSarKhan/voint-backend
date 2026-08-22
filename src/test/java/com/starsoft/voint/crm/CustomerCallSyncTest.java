package com.starsoft.voint.crm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.call.Call;
import com.starsoft.voint.call.CallRepository;
import com.starsoft.voint.call.CallStatus;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CustomerCallSyncTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CallRepository callRepository;

    private static final UUID CES_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void testCustomerCreationAndPhoneLookup() {
        String testPhone = "+994509998877";
        Customer customer = Customer.builder()
                .tenantId(CES_TENANT_ID)
                .phoneNumber(testPhone)
                .name("Kamil Həsənov")
                .notes("Buldozer icarəsi soruşdu")
                .build();

        Customer saved = customerRepository.save(customer);
        assertNotNull(saved.getId());

        Optional<Customer> found = customerRepository.findByTenantIdAndPhoneNumber(CES_TENANT_ID, testPhone);
        assertTrue(found.isPresent());
        assertEquals("Kamil Həsənov", found.get().getName());
        assertEquals("Buldozer icarəsi soruşdu", found.get().getNotes());

        List<Customer> batch = customerRepository.findByTenantIdAndPhoneNumberIn(CES_TENANT_ID, List.of(testPhone, "+994000000000"));
        assertEquals(1, batch.size());
        assertEquals("Kamil Həsənov", batch.get(0).getName());
    }

    @Test
    void testCallLinkedToCustomer() {
        String phone = "+994551112233";
        Customer customer = customerRepository.save(Customer.builder()
                .tenantId(CES_TENANT_ID)
                .phoneNumber(phone)
                .name("Aysel Məmmədova")
                .notes("Kran sifarişi")
                .build());

        Call call = callRepository.save(Call.builder()
                .tenantId(CES_TENANT_ID)
                .callerNumber(phone)
                .languageDetected("az")
                .status(CallStatus.RESOLVED)
                .durationSeconds(85)
                .build());

        assertNotNull(call.getId());

        List<Call> calls = callRepository.findByTenantIdOrderByStartedAtDesc(CES_TENANT_ID);
        assertTrue(calls.stream().anyMatch(c -> c.getId().equals(call.getId())));

        long count = callRepository.countByTenantIdAndCallerNumber(CES_TENANT_ID, phone);
        assertEquals(1, count);
    }
}
