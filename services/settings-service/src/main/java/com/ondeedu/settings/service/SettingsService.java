package com.ondeedu.settings.service;

import com.ondeedu.settings.dto.SettingsDto;
import com.ondeedu.settings.dto.UpdateSettingsRequest;
import com.ondeedu.settings.entity.TenantSettings;
import com.ondeedu.settings.mapper.SettingsMapper;
import com.ondeedu.settings.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settingsRepository;
    private final SettingsMapper settingsMapper;

    @Transactional(readOnly = true)
    @Cacheable(value = "settings", key = "'tenant-settings'")
    public SettingsDto getSettings() {
        TenantSettings settings = settingsRepository.findAll().stream().findFirst()
                .orElseGet(() -> TenantSettings.builder().build());
        return settingsMapper.toDto(settings);
    }

    @Transactional
    @CacheEvict(value = "settings", key = "'tenant-settings'")
    public SettingsDto upsertSettings(UpdateSettingsRequest request) {
        TenantSettings settings = settingsRepository.findAll().stream().findFirst()
                .orElse(TenantSettings.builder().build());
        settingsMapper.updateEntity(settings, request);
        settings = settingsRepository.save(settings);
        log.info("Updated tenant settings");
        return settingsMapper.toDto(settings);
    }
}
