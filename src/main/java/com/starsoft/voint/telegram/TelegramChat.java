package com.starsoft.voint.telegram;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One Telegram chat (a person or a group) a tenant has linked to receive call notifications. */
@Entity
@Table(name = "tenant_telegram_chats")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramChat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Telegram's own chat id - positive for a private chat, negative for a group. */
    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    /** Whatever Telegram reports as the chat's display name at link time - shown in the panel so an admin can tell chats apart. */
    private String label;

    @Column(name = "linked_at", nullable = false)
    @Builder.Default
    private Instant linkedAt = Instant.now();
}
