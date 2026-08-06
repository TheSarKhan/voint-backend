package com.starsoft.voint.settings;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.starsoft.voint.tenant.Tenant;
import com.starsoft.voint.tenant.TenantRepository;

import lombok.RequiredArgsConstructor;

/**
 * Bir istifadəçinin giriş etdiyi panelin ünvanını qurur.
 *
 * <p>Nə üçün ayrıca komponent: bu məntiq əvvəl PanelUserService-in içində gizli idi və domen
 * dəyişəndə (sarkhan.az → voint.az) geridə qaldı — xoş gəlmisiniz və şifrə e-poçtları
 * {@code voint-admin.voint.az} kimi ARTIQ MÖVCUD OLMAYAN ünvanlara link verirdi. İndi tək yerdədir,
 * baza domeni isə {@link SettingKey#PANEL_DOMAIN}-dən (admin paneldən dəyişilə bilən) gəlir.
 *
 * <p>Sxem: platforma admini → {@code admin.<domen>}, müəssisə → {@code <subdomain>.<domen>},
 * subdomain yoxdursa → {@code app.<domen>}.
 */
@Component
@RequiredArgsConstructor
public class PanelUrls {

    private final PlatformSettingsService settings;
    private final TenantRepository tenantRepository;

    /** @param tenantId null = platforma admini */
    public String forTenant(UUID tenantId) {
        String domain = settings.get(SettingKey.PANEL_DOMAIN);
        if (tenantId == null) {
            return "https://admin." + domain;
        }
        return tenantRepository.findById(tenantId)
                .map(Tenant::getSubdomain)
                .filter(sub -> sub != null && !sub.isBlank())
                .map(sub -> "https://" + sub + "." + domain)
                .orElse("https://app." + domain);
    }
}
