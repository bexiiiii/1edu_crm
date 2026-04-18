package com.ondeedu.settings.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.payment.ApiPayRecipientField;
import com.ondeedu.common.payment.KpayRecipientField;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.settings.client.GoogleDriveBackupClient;
import com.ondeedu.settings.dto.AisarSettingsDto;
import com.ondeedu.settings.client.FileServiceClient;
import com.ondeedu.settings.dto.ApiPaySettingsDto;
import com.ondeedu.settings.dto.CloudBackupRunResultDto;
import com.ondeedu.settings.dto.FtelecomSettingsDto;
import com.ondeedu.settings.dto.GoogleDriveBackupSettingsDto;
import com.ondeedu.settings.dto.InternalAisarConfigDto;
import com.ondeedu.settings.dto.InternalApiPayConfigDto;
import com.ondeedu.settings.dto.InternalFtelecomConfigDto;
import com.ondeedu.settings.dto.InternalKpayConfigDto;
import com.ondeedu.settings.dto.InternalZadarmaConfigDto;
import com.ondeedu.settings.dto.KpaySettingsDto;
import com.ondeedu.settings.dto.SettingsDto;
import com.ondeedu.settings.dto.UpdateGoogleDriveBackupSettingsRequest;
import com.ondeedu.settings.dto.UpdateAisarSettingsRequest;
import com.ondeedu.settings.dto.UpdateApiPaySettingsRequest;
import com.ondeedu.settings.dto.UpdateFtelecomSettingsRequest;
import com.ondeedu.settings.dto.UpdateKpaySettingsRequest;
import com.ondeedu.settings.dto.UpdateSettingsRequest;
import com.ondeedu.settings.dto.UpdateYandexDiskBackupSettingsRequest;
import com.ondeedu.settings.dto.UpdateZadarmaSettingsRequest;
import com.ondeedu.settings.dto.YandexDiskBackupSettingsDto;
import com.ondeedu.settings.dto.ZadarmaSettingsDto;
import com.ondeedu.settings.entity.TenantSettings;
import com.ondeedu.settings.mapper.SettingsMapper;
import com.ondeedu.settings.repository.SettingsRepository;
import com.ondeedu.settings.client.YandexDiskBackupClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String AISAR_SIGNATURE_HEADER = "X-AISAR-Signature";
    private static final String AISAR_SIGNATURE_ALGORITHM = "HMAC-SHA256";
    private static final String FTELECOM_TOKEN_FIELD = "crm_token";
    private static final String ZADARMA_SIGNATURE_HEADER = "Signature";
    private static final String ZADARMA_SIGNATURE_ALGORITHM = "HMAC-SHA1 (base64)";
    private static final String ZADARMA_VALIDATION_MODE = "GET ?zd_echo=<random>";

    private final SettingsRepository settingsRepository;
    private final SettingsMapper settingsMapper;
    private final FileServiceClient fileServiceClient;
    private final TenantBackupExporter tenantBackupExporter;
    private final GoogleDriveBackupClient googleDriveBackupClient;
    private final YandexDiskBackupClient yandexDiskBackupClient;

    @Value("${ondeedu.integrations.public-base-url:https://api.1edu.kz}")
    private String integrationsPublicBaseUrl;

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

    @Transactional(readOnly = true)
    @Cacheable(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('kpay-settings')")
    public KpaySettingsDto getKpaySettings() {
        TenantSettings settings = getOrCreateSettings();

        boolean configured = StringUtils.hasText(settings.getKpayApiKey())
                && StringUtils.hasText(settings.getKpayApiSecret())
                && StringUtils.hasText(settings.getKpayApiBaseUrl());

        return KpaySettingsDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getKpayEnabled()))
                .configured(configured)
                .merchantId(settings.getKpayMerchantId())
                .apiBaseUrl(settings.getKpayApiBaseUrl())
                .recipientField(settings.getKpayRecipientField())
                .apiKeyMasked(maskSecret(settings.getKpayApiKey()))
                .apiSecretMasked(maskSecret(settings.getKpayApiSecret()))
                .build();
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public KpaySettingsDto upsertKpaySettings(UpdateKpaySettingsRequest request) {
        TenantSettings settings = getOrCreateSettings();

        if (request.getEnabled() != null) {
            settings.setKpayEnabled(request.getEnabled());
        }

        if (request.getMerchantId() != null) {
            settings.setKpayMerchantId(trimToNull(request.getMerchantId()));
        }

        if (request.getApiBaseUrl() != null) {
            settings.setKpayApiBaseUrl(trimToNull(request.getApiBaseUrl()));
        }

        if (request.getRecipientField() != null) {
            settings.setKpayRecipientField(request.getRecipientField());
        }

        if (request.getApiKey() != null) {
            settings.setKpayApiKey(trimToNull(request.getApiKey()));
        }

        if (request.getApiSecret() != null) {
            settings.setKpayApiSecret(trimToNull(request.getApiSecret()));
        }

        if (settings.getKpayRecipientField() == null) {
            settings.setKpayRecipientField(KpayRecipientField.PARENT_PHONE);
        }

        if (!StringUtils.hasText(settings.getKpayApiBaseUrl())) {
            settings.setKpayApiBaseUrl("https://kpayapp.kz/api");
        }

        if (Boolean.TRUE.equals(settings.getKpayEnabled())) {
            if (!StringUtils.hasText(settings.getKpayApiKey()) || !StringUtils.hasText(settings.getKpayApiSecret())) {
                throw new BusinessException("KPAY_KEYS_REQUIRED",
                        "KPay API key and secret are required when integration is enabled");
            }
            if (!StringUtils.hasText(settings.getKpayApiBaseUrl())) {
                throw new BusinessException("KPAY_BASE_URL_REQUIRED",
                        "KPay base URL is required when integration is enabled");
            }
        }

        settingsRepository.save(settings);
        log.info("Updated KPAY integration settings");
        return getKpaySettings();
    }

    @Transactional(readOnly = true)
    public InternalKpayConfigDto getInternalKpayConfig() {
        TenantSettings settings = getOrCreateSettings();
        return InternalKpayConfigDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getKpayEnabled()))
                .merchantId(settings.getKpayMerchantId())
                .apiBaseUrl(settings.getKpayApiBaseUrl())
                .recipientField(settings.getKpayRecipientField() != null
                        ? settings.getKpayRecipientField()
                        : KpayRecipientField.PARENT_PHONE)
                .apiKey(settings.getKpayApiKey())
                .apiSecret(settings.getKpayApiSecret())
                .build();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('apipay-settings')")
    public ApiPaySettingsDto getApiPaySettings() {
        TenantSettings settings = getOrCreateSettings();

        boolean configured = StringUtils.hasText(settings.getApipayApiKey())
                && StringUtils.hasText(settings.getApipayApiBaseUrl())
                && StringUtils.hasText(settings.getApipayWebhookSecret());

        return ApiPaySettingsDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getApipayEnabled()))
                .configured(configured)
                .apiBaseUrl(settings.getApipayApiBaseUrl())
                .recipientField(settings.getApipayRecipientField())
                .apiKeyMasked(maskSecret(settings.getApipayApiKey()))
                .webhookSecretMasked(maskSecret(settings.getApipayWebhookSecret()))
                .build();
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public ApiPaySettingsDto upsertApiPaySettings(UpdateApiPaySettingsRequest request) {
        TenantSettings settings = getOrCreateSettings();

        if (request.getEnabled() != null) {
            settings.setApipayEnabled(request.getEnabled());
        }
        if (request.getApiBaseUrl() != null) {
            settings.setApipayApiBaseUrl(trimToNull(request.getApiBaseUrl()));
        }
        if (request.getRecipientField() != null) {
            settings.setApipayRecipientField(request.getRecipientField());
        }
        if (request.getApiKey() != null) {
            settings.setApipayApiKey(trimToNull(request.getApiKey()));
        }
        if (request.getWebhookSecret() != null) {
            settings.setApipayWebhookSecret(trimToNull(request.getWebhookSecret()));
        }

        if (settings.getApipayRecipientField() == null) {
            settings.setApipayRecipientField(ApiPayRecipientField.PARENT_PHONE);
        }
        if (!StringUtils.hasText(settings.getApipayApiBaseUrl())) {
            settings.setApipayApiBaseUrl("https://bpapi.bazarbay.site/api/v1");
        }

        if (Boolean.TRUE.equals(settings.getApipayEnabled())) {
            if (!StringUtils.hasText(settings.getApipayApiKey())) {
                throw new BusinessException("APIPAY_API_KEY_REQUIRED",
                        "ApiPay API key is required when integration is enabled");
            }
            if (!StringUtils.hasText(settings.getApipayApiBaseUrl())) {
                throw new BusinessException("APIPAY_BASE_URL_REQUIRED",
                        "ApiPay base URL is required when integration is enabled");
            }
            if (!StringUtils.hasText(settings.getApipayWebhookSecret())) {
                throw new BusinessException("APIPAY_WEBHOOK_SECRET_REQUIRED",
                        "ApiPay webhook secret is required when integration is enabled");
            }
        }

        settingsRepository.save(settings);
        log.info("Updated ApiPay integration settings");
        return getApiPaySettings();
    }

    @Transactional(readOnly = true)
    public InternalApiPayConfigDto getInternalApiPayConfig() {
        TenantSettings settings = getOrCreateSettings();
        return InternalApiPayConfigDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getApipayEnabled()))
                .apiBaseUrl(settings.getApipayApiBaseUrl())
                .recipientField(settings.getApipayRecipientField() != null
                        ? settings.getApipayRecipientField()
                        : ApiPayRecipientField.PARENT_PHONE)
                .apiKey(settings.getApipayApiKey())
                .webhookSecret(settings.getApipayWebhookSecret())
                .build();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('aisar-settings')")
    public AisarSettingsDto getAisarSettings() {
        TenantSettings settings = getOrCreateSettings();

        boolean configured = StringUtils.hasText(settings.getAisarWebhookSecret());

        return AisarSettingsDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getAisarEnabled()))
                .configured(configured)
                .apiBaseUrl(settings.getAisarApiBaseUrl())
                .apiKeyMasked(maskSecret(settings.getAisarApiKey()))
                .webhookSecretMasked(maskSecret(settings.getAisarWebhookSecret()))
                .webhookUrl(buildAisarWebhookUrl())
                .signatureHeader(AISAR_SIGNATURE_HEADER)
                .signatureAlgorithm(AISAR_SIGNATURE_ALGORITHM)
                .build();
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public AisarSettingsDto upsertAisarSettings(UpdateAisarSettingsRequest request) {
        TenantSettings settings = getOrCreateSettings();

        if (request.getEnabled() != null) {
            settings.setAisarEnabled(request.getEnabled());
        }
        if (request.getApiBaseUrl() != null) {
            settings.setAisarApiBaseUrl(trimToNull(request.getApiBaseUrl()));
        }
        if (request.getApiKey() != null) {
            settings.setAisarApiKey(trimToNull(request.getApiKey()));
        }
        if (request.getWebhookSecret() != null) {
            settings.setAisarWebhookSecret(trimToNull(request.getWebhookSecret()));
        }

        if (!StringUtils.hasText(settings.getAisarApiBaseUrl())) {
            settings.setAisarApiBaseUrl("https://aisar.app");
        }

        if (Boolean.TRUE.equals(settings.getAisarEnabled())
                && !StringUtils.hasText(settings.getAisarWebhookSecret())) {
            throw new BusinessException("AISAR_WEBHOOK_SECRET_REQUIRED",
                    "AISAR webhook secret is required when integration is enabled");
        }

        settingsRepository.save(settings);
        log.info("Updated AISAR integration settings");
        return getAisarSettings();
    }

    @Transactional(readOnly = true)
    public InternalAisarConfigDto getInternalAisarConfig() {
        TenantSettings settings = getOrCreateSettings();
        return InternalAisarConfigDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getAisarEnabled()))
                .apiBaseUrl(settings.getAisarApiBaseUrl())
                .apiKey(settings.getAisarApiKey())
                .webhookSecret(settings.getAisarWebhookSecret())
                .build();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('ftelecom-settings')")
    public FtelecomSettingsDto getFtelecomSettings() {
        TenantSettings settings = getOrCreateSettings();

        boolean configured = StringUtils.hasText(settings.getFtelecomCrmToken());

        return FtelecomSettingsDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getFtelecomEnabled()))
                .configured(configured)
                .apiBaseUrl(settings.getFtelecomApiBaseUrl())
                .crmTokenMasked(maskSecret(settings.getFtelecomCrmToken()))
                .webhookUrl(buildFtelecomWebhookUrl())
                .tokenField(FTELECOM_TOKEN_FIELD)
                .build();
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public FtelecomSettingsDto upsertFtelecomSettings(UpdateFtelecomSettingsRequest request) {
        TenantSettings settings = getOrCreateSettings();

        if (request.getEnabled() != null) {
            settings.setFtelecomEnabled(request.getEnabled());
        }
        if (request.getApiBaseUrl() != null) {
            settings.setFtelecomApiBaseUrl(trimToNull(request.getApiBaseUrl()));
        }
        if (request.getCrmToken() != null) {
            settings.setFtelecomCrmToken(trimToNull(request.getCrmToken()));
        }

        if (!StringUtils.hasText(settings.getFtelecomApiBaseUrl())) {
            settings.setFtelecomApiBaseUrl("https://api.vpbx.ftel.kz");
        }

        if (Boolean.TRUE.equals(settings.getFtelecomEnabled())
                && !StringUtils.hasText(settings.getFtelecomCrmToken())) {
            throw new BusinessException("FTELECOM_CRM_TOKEN_REQUIRED",
                    "Freedom Telecom CRM token is required when integration is enabled");
        }

        settingsRepository.save(settings);
        log.info("Updated Freedom Telecom integration settings");
        return getFtelecomSettings();
    }

    @Transactional(readOnly = true)
    public InternalFtelecomConfigDto getInternalFtelecomConfig() {
        TenantSettings settings = getOrCreateSettings();
        return InternalFtelecomConfigDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getFtelecomEnabled()))
                .apiBaseUrl(settings.getFtelecomApiBaseUrl())
                .crmToken(settings.getFtelecomCrmToken())
                .build();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('zadarma-settings')")
    public ZadarmaSettingsDto getZadarmaSettings() {
        TenantSettings settings = getOrCreateSettings();

        boolean configured = StringUtils.hasText(settings.getZadarmaUserKey())
                && StringUtils.hasText(settings.getZadarmaUserSecret());

        return ZadarmaSettingsDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getZadarmaEnabled()))
                .configured(configured)
                .apiBaseUrl(settings.getZadarmaApiBaseUrl())
                .userKeyMasked(maskSecret(settings.getZadarmaUserKey()))
                .userSecretMasked(maskSecret(settings.getZadarmaUserSecret()))
                .webhookUrl(buildZadarmaWebhookUrl())
                .validationMode(ZADARMA_VALIDATION_MODE)
                .signatureHeader(ZADARMA_SIGNATURE_HEADER)
                .signatureAlgorithm(ZADARMA_SIGNATURE_ALGORITHM)
                .build();
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public ZadarmaSettingsDto upsertZadarmaSettings(UpdateZadarmaSettingsRequest request) {
        TenantSettings settings = getOrCreateSettings();

        if (request.getEnabled() != null) {
            settings.setZadarmaEnabled(request.getEnabled());
        }
        if (request.getApiBaseUrl() != null) {
            settings.setZadarmaApiBaseUrl(trimToNull(request.getApiBaseUrl()));
        }
        if (request.getUserKey() != null) {
            settings.setZadarmaUserKey(trimToNull(request.getUserKey()));
        }
        if (request.getUserSecret() != null) {
            settings.setZadarmaUserSecret(trimToNull(request.getUserSecret()));
        }

        if (!StringUtils.hasText(settings.getZadarmaApiBaseUrl())) {
            settings.setZadarmaApiBaseUrl("https://api.zadarma.com");
        }

        if (Boolean.TRUE.equals(settings.getZadarmaEnabled())) {
            if (!StringUtils.hasText(settings.getZadarmaUserKey())) {
                throw new BusinessException("ZADARMA_USER_KEY_REQUIRED",
                        "Zadarma user key is required when integration is enabled");
            }
            if (!StringUtils.hasText(settings.getZadarmaUserSecret())) {
                throw new BusinessException("ZADARMA_USER_SECRET_REQUIRED",
                        "Zadarma user secret is required when integration is enabled");
            }
        }

        settingsRepository.save(settings);
        log.info("Updated Zadarma integration settings");
        return getZadarmaSettings();
    }

    @Transactional(readOnly = true)
    public InternalZadarmaConfigDto getInternalZadarmaConfig() {
        TenantSettings settings = getOrCreateSettings();
        return InternalZadarmaConfigDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getZadarmaEnabled()))
                .apiBaseUrl(settings.getZadarmaApiBaseUrl())
                .userKey(settings.getZadarmaUserKey())
                .userSecret(settings.getZadarmaUserSecret())
                .build();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('google-drive-backup-settings')")
    public GoogleDriveBackupSettingsDto getGoogleDriveBackupSettings() {
        TenantSettings settings = getOrCreateSettings();

        boolean configured = StringUtils.hasText(settings.getGoogleDriveBackupAccessToken());

        return GoogleDriveBackupSettingsDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getGoogleDriveBackupEnabled()))
                .configured(configured)
                .folderId(settings.getGoogleDriveBackupFolderId())
                .accessTokenMasked(maskSecret(settings.getGoogleDriveBackupAccessToken()))
                .lastBackupAt(settings.getGoogleDriveLastBackupAt())
                .build();
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public GoogleDriveBackupSettingsDto upsertGoogleDriveBackupSettings(
            UpdateGoogleDriveBackupSettingsRequest request
    ) {
        TenantSettings settings = getOrCreateSettings();

        if (request.getEnabled() != null) {
            settings.setGoogleDriveBackupEnabled(request.getEnabled());
        }
        if (request.getFolderId() != null) {
            settings.setGoogleDriveBackupFolderId(trimToNull(request.getFolderId()));
        }
        if (request.getAccessToken() != null) {
            settings.setGoogleDriveBackupAccessToken(trimToNull(request.getAccessToken()));
        }

        if (Boolean.TRUE.equals(settings.getGoogleDriveBackupEnabled())
                && !StringUtils.hasText(settings.getGoogleDriveBackupAccessToken())) {
            throw new BusinessException("GOOGLE_DRIVE_ACCESS_TOKEN_REQUIRED",
                    "Google Drive access token is required when backup is enabled");
        }

        settingsRepository.save(settings);
        log.info("Updated Google Drive backup settings");
        return getGoogleDriveBackupSettings();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('yandex-disk-backup-settings')")
    public YandexDiskBackupSettingsDto getYandexDiskBackupSettings() {
        TenantSettings settings = getOrCreateSettings();

        boolean configured = StringUtils.hasText(settings.getYandexDiskBackupAccessToken());

        return YandexDiskBackupSettingsDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getYandexDiskBackupEnabled()))
                .configured(configured)
                .folderPath(settings.getYandexDiskBackupFolderPath())
                .accessTokenMasked(maskSecret(settings.getYandexDiskBackupAccessToken()))
                .lastBackupAt(settings.getYandexDiskLastBackupAt())
                .build();
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public YandexDiskBackupSettingsDto upsertYandexDiskBackupSettings(
            UpdateYandexDiskBackupSettingsRequest request
    ) {
        TenantSettings settings = getOrCreateSettings();

        if (request.getEnabled() != null) {
            settings.setYandexDiskBackupEnabled(request.getEnabled());
        }
        if (request.getFolderPath() != null) {
            settings.setYandexDiskBackupFolderPath(trimToNull(request.getFolderPath()));
        }
        if (request.getAccessToken() != null) {
            settings.setYandexDiskBackupAccessToken(trimToNull(request.getAccessToken()));
        }

        if (!StringUtils.hasText(settings.getYandexDiskBackupFolderPath())) {
            settings.setYandexDiskBackupFolderPath("disk:/1edu-backups");
        }

        if (Boolean.TRUE.equals(settings.getYandexDiskBackupEnabled())
                && !StringUtils.hasText(settings.getYandexDiskBackupAccessToken())) {
            throw new BusinessException("YANDEX_DISK_ACCESS_TOKEN_REQUIRED",
                    "Yandex Disk OAuth token is required when backup is enabled");
        }

        settingsRepository.save(settings);
        log.info("Updated Yandex Disk backup settings");
        return getYandexDiskBackupSettings();
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public CloudBackupRunResultDto runGoogleDriveBackup() {
        TenantSettings settings = getOrCreateSettings();
        if (!Boolean.TRUE.equals(settings.getGoogleDriveBackupEnabled())) {
            throw new BusinessException("GOOGLE_DRIVE_BACKUP_DISABLED",
                    "Google Drive backup is disabled",
                    org.springframework.http.HttpStatus.CONFLICT);
        }

        TenantBackupArtifact artifact = tenantBackupExporter.exportCurrentTenantSnapshot();
        var uploadResult = googleDriveBackupClient.upload(
                settings.getGoogleDriveBackupAccessToken(),
                settings.getGoogleDriveBackupFolderId(),
                artifact
        );

        settings.setGoogleDriveLastBackupAt(artifact.createdAt());
        settingsRepository.save(settings);

        return CloudBackupRunResultDto.builder()
                .provider("GOOGLE_DRIVE")
                .fileName(uploadResult.fileName())
                .remoteId(uploadResult.fileId())
                .completedAt(artifact.createdAt())
                .build();
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public CloudBackupRunResultDto runYandexDiskBackup() {
        TenantSettings settings = getOrCreateSettings();
        if (!Boolean.TRUE.equals(settings.getYandexDiskBackupEnabled())) {
            throw new BusinessException("YANDEX_DISK_BACKUP_DISABLED",
                    "Yandex Disk backup is disabled",
                    org.springframework.http.HttpStatus.CONFLICT);
        }

        TenantBackupArtifact artifact = tenantBackupExporter.exportCurrentTenantSnapshot();
        var uploadResult = yandexDiskBackupClient.upload(
                settings.getYandexDiskBackupAccessToken(),
                settings.getYandexDiskBackupFolderPath(),
                artifact
        );

        settings.setYandexDiskLastBackupAt(artifact.createdAt());
        settingsRepository.save(settings);

        return CloudBackupRunResultDto.builder()
                .provider("YANDEX_DISK")
                .fileName(artifact.fileName())
                .remotePath(uploadResult.remotePath())
                .completedAt(artifact.createdAt())
                .build();
    }

    private TenantSettings getOrCreateSettings() {
        return settingsRepository.findFirstBy()
                .orElseGet(() -> settingsRepository.save(TenantSettings.builder().build()));
    }

    private String maskSecret(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() <= 6) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 3) + "*".repeat(value.length() - 6) + value.substring(value.length() - 3);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildAisarWebhookUrl() {
        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(integrationsPublicBaseUrl)) {
            return null;
        }
        return integrationsPublicBaseUrl.replaceAll("/+$", "") + "/internal/aisar/webhook/" + tenantId;
    }

    private String buildFtelecomWebhookUrl() {
        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(integrationsPublicBaseUrl)) {
            return null;
        }
        return integrationsPublicBaseUrl.replaceAll("/+$", "") + "/internal/ftelecom/webhook/" + tenantId;
    }

    private String buildZadarmaWebhookUrl() {
        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(integrationsPublicBaseUrl)) {
            return null;
        }
        return integrationsPublicBaseUrl.replaceAll("/+$", "") + "/internal/zadarma/webhook/" + tenantId;
    }
}
