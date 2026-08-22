package com.starsoft.voint.crm;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByTenantIdOrderByLastSeenAtDesc(UUID tenantId);

    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Customer> findByTenantIdAndPhoneNumber(UUID tenantId, String phoneNumber);

    List<Customer> findByTenantIdAndPhoneNumberIn(UUID tenantId, Collection<String> phoneNumbers);
}

