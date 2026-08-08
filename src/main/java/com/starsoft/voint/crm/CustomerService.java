package com.starsoft.voint.crm;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.crm.dto.CustomerCreateRequest;
import com.starsoft.voint.crm.dto.CustomerUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Bir zəng bitəndə çağırılır: bu nömrə üçün artıq kart varsa "son görülmə"nü təzələyir, yoxdursa
     * yenisini açır. {@code callCount} ayrıca yazılmır — {@link CustomerController} onu hər dəfə
     * {@code calls} cədvəlindən nömrə üzrə sayır, ona görə bir kartın açılması kifayətdir.
     */
    @Transactional
    public Customer findOrCreateByPhone(UUID tenantId, String phoneNumber) {
        String normalized = phoneNumber.trim();
        Instant now = Instant.now();
        return customerRepository.findByTenantIdAndPhoneNumber(tenantId, normalized)
                .map(existing -> {
                    existing.setLastSeenAt(now);
                    return customerRepository.save(existing);
                })
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .tenantId(tenantId)
                        .phoneNumber(normalized)
                        .firstSeenAt(now)
                        .lastSeenAt(now)
                        .build()));
    }

    @Transactional(readOnly = true)
    public List<Customer> list(UUID tenantId) {
        return customerRepository.findByTenantIdOrderByLastSeenAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public Customer get(UUID tenantId, UUID customerId) {
        return customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> NotFoundException.of("Customer", customerId));
    }

    @Transactional
    public Customer create(UUID tenantId, CustomerCreateRequest request) {
        Customer customer = Customer.builder()
                .tenantId(tenantId)
                .phoneNumber(request.phoneNumber())
                .name(request.name())
                .notes(request.notes())
                .build();
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer update(UUID tenantId, UUID customerId, CustomerUpdateRequest request) {
        Customer customer = get(tenantId, customerId);
        if (request.phoneNumber() != null) customer.setPhoneNumber(request.phoneNumber());
        if (request.name() != null) customer.setName(request.name());
        if (request.notes() != null) customer.setNotes(request.notes());
        return customerRepository.save(customer);
    }
}
