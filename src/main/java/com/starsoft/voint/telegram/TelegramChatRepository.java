package com.starsoft.voint.telegram;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramChatRepository extends JpaRepository<TelegramChat, UUID> {

    List<TelegramChat> findByTenantIdOrderByLinkedAtDesc(UUID tenantId);

    Optional<TelegramChat> findByTenantIdAndChatId(UUID tenantId, Long chatId);

    Optional<TelegramChat> findByIdAndTenantId(UUID id, UUID tenantId);
}
