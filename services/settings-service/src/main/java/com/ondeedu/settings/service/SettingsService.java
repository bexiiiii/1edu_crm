package com.ondeedu.settings.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.settings.client.FileServiceClient;
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
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settingsRepository;
    private final SettingsMapper settingsMapper;
    private final FileServiceClient fileServiceClient;

    @Transactional(readOnly = true)
    @Cacheable(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('tenant-settings')")
    public SettingsDto getSettings() {
        TenantSettings settings = getOrCreateSettings();
        return settingsMapper.toDto(settings);
    }

    @Transactional
    @CacheEvict(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('tenant-settings')")
    public SettingsDto upsertSettings(UpdateSettingsRequest request) {
        TenantSettings settings = getOrCreateSettings();
        settingsMapper.updateEntity(settings, request);
        settings = settingsRepository.save(settings);
        log.info("Updated tenant settings");
        return settingsMapper.toDto(settings);
    }

    @Transactional
    @CacheEvict(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('tenant-settings')")
    public SettingsDto uploadLogo(MultipartFile file, String bearerToken) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("LOGO_FILE_REQUIRED", "Logo file is required");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new BusinessException("INVALID_LOGO_FILE", "Logo must be an image");
        }

        TenantSettings settings = getOrCreateSettings();
        settings.setLogoUrl(fileServiceClient.uploadLogo(file, bearerToken));
        settings = settingsRepository.save(settings);
        log.info("Updated tenant logo");
        return settingsMapper.toDto(settings);
    }

    private TenantSettings getOrCreateSettings() {
        return settingsRepository.findFirstBy()
                .orElseGet(() -> settingsRepository.save(TenantSettings.builder().build()));
    }
}
