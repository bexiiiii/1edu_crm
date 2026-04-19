package com.ondeedu.settings.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.payment.ApiPayRecipientField;
import com.ondeedu.common.payment.KpayRecipientField;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.common.tenant.TenantSchemaResolver;
import com.ondeedu.settings.client.GoogleDriveBackupClient;
import com.ondeedu.settings.client.GoogleDriveOAuthClient;
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
import com.ondeedu.settings.client.YandexDiskOAuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String APIPAY_SIGNATURE_HEADER = "X-Webhook-Signature";
    private static final String APIPAY_SIGNATURE_ALGORITHM = "HMAC-SHA256";
    private static final String AISAR_SIGNATURE_HEADER = "X-AISAR-Signature";
    private static final String AISAR_SIGNATURE_ALGORITHM = "HMAC-SHA256";
    private static final String FTELECOM_TOKEN_FIELD = "crm_token";
    private static final String ZADARMA_SIGNATURE_HEADER = "Signature";
    private static final String ZADARMA_SIGNATURE_ALGORITHM = "HMAC-SHA1 (base64)";
    private static final String ZADARMA_VALIDATION_MODE = "GET ?zd_echo=<random>";
    private static final String OAUTH_PROVIDER_GOOGLE_DRIVE = "google_drive";
    private static final String OAUTH_PROVIDER_YANDEX_DISK = "yandex_disk";

    private final SettingsRepository settingsRepository;
    private final SettingsMapper settingsMapper;
    private final FileServiceClient fileServiceClient;
    private final TenantBackupExporter tenantBackupExporter;
    private final GoogleDriveBackupClient googleDriveBackupClient;
    private final GoogleDriveOAuthClient googleDriveOAuthClient;
    private final YandexDiskBackupClient yandexDiskBackupClient;
    private final YandexDiskOAuthClient yandexDiskOAuthClient;

    @Value("${ondeedu.integrations.public-base-url:https://api.1edu.kz}")
    private String integrationsPublicBaseUrl;

    @Value("${ondeedu.integrations.oauth.state-secret:change-me-oauth-state-secret}")
    private String oauthStateSecret;

    @Value("${ondeedu.integrations.google-drive.oauth.client-id:}")
    private String googleDriveOAuthClientId;

    @Value("${ondeedu.integrations.google-drive.oauth.client-secret:}")
    private String googleDriveOAuthClientSecret;

    @Value("${ondeedu.integrations.google-drive.oauth.scope:https://www.googleapis.com/auth/drive.file}")
    private String googleDriveOAuthScope;

    @Value("${ondeedu.integrations.yandex-disk.oauth.client-id:}")
    private String yandexDiskOAuthClientId;

    @Value("${ondeedu.integrations.yandex-disk.oauth.client-secret:}")
    private String yandexDiskOAuthClientSecret;

    @Value("${ondeedu.integrations.yandex-disk.oauth.scope:cloud_api:disk.read cloud_api:disk.write}")
    private String yandexDiskOAuthScope;

    @Value("${minio.public-url:}")
    private String minioPublicUrl;

    @Value("${minio.url:http://minio:9000}")
    private String minioUrl;

    @Value("${minio.bucket:ondeedu-files}")
    private String minioBucket;

    @Transactional(readOnly = true)
    @Cacheable(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('tenant-settings')")
    public SettingsDto getSettings() {
        TenantSettings settings = getOrCreateSettings();
        return toSettingsDto(settings);
    }

    @Transactional
    @CacheEvict(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('tenant-settings')")
    public SettingsDto upsertSettings(UpdateSettingsRequest request) {
        TenantSettings settings = getOrCreateSettings();
        settingsMapper.updateEntity(settings, request);
        settings = settingsRepository.save(settings);
        log.info("Updated tenant settings");
        return toSettingsDto(settings);
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
        return toSettingsDto(settings);
    }

    private SettingsDto toSettingsDto(TenantSettings settings) {
        SettingsDto dto = settingsMapper.toDto(settings);
        dto.setLogoUrl(normalizeMediaUrl(dto.getLogoUrl()));
        return dto;
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
            && StringUtils.hasText(settings.getApipayApiBaseUrl());

        return ApiPaySettingsDto.builder()
                .enabled(Boolean.TRUE.equals(settings.getApipayEnabled()))
                .configured(configured)
                .apiBaseUrl(settings.getApipayApiBaseUrl())
                .recipientField(settings.getApipayRecipientField())
                .apiKeyMasked(maskSecret(settings.getApipayApiKey()))
                .webhookSecretMasked(maskSecret(settings.getApipayWebhookSecret()))
            .webhookUrl(buildApiPayWebhookUrl())
            .signatureHeader(APIPAY_SIGNATURE_HEADER)
            .signatureAlgorithm(APIPAY_SIGNATURE_ALGORITHM)
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
                settings.setApipayWebhookSecret(generateWebhookSecret());
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
                .oauthConnectUrl(buildGoogleDriveOAuthConnectUrlSafe())
                .folderId(settings.getGoogleDriveBackupFolderId())
                .accessTokenMasked(maskSecret(settings.getGoogleDriveBackupAccessToken()))
                .lastBackupAt(settings.getGoogleDriveLastBackupAt())
                .build();
    }

    @Transactional(readOnly = true)
    public String getGoogleDriveBackupOAuthConnectUrl() {
        ensureGoogleDriveOAuthConfigured();
        String tenantId = requireTenantId();
        return buildGoogleDriveOAuthConnectUrl(tenantId);
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public void completeGoogleDriveBackupOAuth(String code, String state) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("GOOGLE_DRIVE_OAUTH_CODE_REQUIRED",
                    "Google Drive OAuth code is required");
        }

        ensureGoogleDriveOAuthConfigured();
        OAuthStateData stateData = parseAndVerifyOAuthState(state, OAUTH_PROVIDER_GOOGLE_DRIVE);
        String schemaName = TenantSchemaResolver.schemaNameForTenantId(stateData.tenantId());
        if (!StringUtils.hasText(schemaName)) {
            throw new BusinessException("GOOGLE_DRIVE_OAUTH_INVALID_TENANT",
                    "Invalid tenant in OAuth state");
        }

        TenantContext.setTenantId(stateData.tenantId());
        TenantContext.setSchemaName(schemaName);
        try {
            String accessToken = googleDriveOAuthClient.exchangeAuthorizationCode(
                    code.trim(),
                    googleDriveOAuthClientId.trim(),
                    googleDriveOAuthClientSecret.trim(),
                    buildGoogleDriveOAuthCallbackUrl()
            ).accessToken();

            TenantSettings settings = getOrCreateSettings();
            settings.setGoogleDriveBackupAccessToken(trimToNull(accessToken));
            settings.setGoogleDriveBackupEnabled(true);
            settingsRepository.save(settings);
            log.info("Google Drive OAuth connected for tenant {}", stateData.tenantId());

            triggerInitialGoogleDriveBackupSafely(stateData.tenantId());
        } finally {
            TenantContext.clear();
        }
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
                .oauthConnectUrl(buildYandexDiskOAuthConnectUrlSafe())
                .folderPath(settings.getYandexDiskBackupFolderPath())
                .accessTokenMasked(maskSecret(settings.getYandexDiskBackupAccessToken()))
                .lastBackupAt(settings.getYandexDiskLastBackupAt())
                .build();
    }

    @Transactional(readOnly = true)
    public String getYandexDiskBackupOAuthConnectUrl() {
        ensureYandexDiskOAuthConfigured();
        String tenantId = requireTenantId();
        return buildYandexDiskOAuthConnectUrl(tenantId);
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public void completeYandexDiskBackupOAuth(String code, String state) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("YANDEX_DISK_OAUTH_CODE_REQUIRED",
                    "Yandex Disk OAuth code is required");
        }

        ensureYandexDiskOAuthConfigured();
        OAuthStateData stateData = parseAndVerifyOAuthState(state, OAUTH_PROVIDER_YANDEX_DISK);
        String schemaName = TenantSchemaResolver.schemaNameForTenantId(stateData.tenantId());
        if (!StringUtils.hasText(schemaName)) {
            throw new BusinessException("YANDEX_DISK_OAUTH_INVALID_TENANT",
                    "Invalid tenant in OAuth state");
        }

        TenantContext.setTenantId(stateData.tenantId());
        TenantContext.setSchemaName(schemaName);
        try {
            String accessToken = yandexDiskOAuthClient.exchangeAuthorizationCode(
                    code.trim(),
                    yandexDiskOAuthClientId.trim(),
                    yandexDiskOAuthClientSecret.trim()
            ).accessToken();

            TenantSettings settings = getOrCreateSettings();
            settings.setYandexDiskBackupAccessToken(trimToNull(accessToken));
            settings.setYandexDiskBackupEnabled(true);
            settingsRepository.save(settings);
            log.info("Yandex Disk OAuth connected for tenant {}", stateData.tenantId());

            triggerInitialYandexDiskBackupSafely(stateData.tenantId());
        } finally {
            TenantContext.clear();
        }
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

    private String normalizeMediaUrl(String rawUrl) {
        String value = trimToNull(rawUrl);
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String publicBase = normalizeBaseUrl(minioPublicUrl);
        String internalBase = normalizeBaseUrl(minioUrl);
        String effectiveBase = StringUtils.hasText(publicBase) ? publicBase : internalBase;
        if (!StringUtils.hasText(effectiveBase)) {
            return value;
        }

        if (value.startsWith("http://") || value.startsWith("https://")) {
            if (StringUtils.hasText(publicBase)
                    && StringUtils.hasText(internalBase)
                    && value.startsWith(internalBase + "/")) {
                return publicBase + value.substring(internalBase.length());
            }
            return value;
        }

        String normalizedPath = value.startsWith("/") ? value.substring(1) : value;
        String bucketPrefix = minioBucket + "/";
        if (normalizedPath.startsWith(bucketPrefix)) {
            return effectiveBase + "/" + normalizedPath;
        }
        return effectiveBase + "/" + minioBucket + "/" + normalizedPath;
    }

    private void triggerInitialGoogleDriveBackupSafely(String tenantId) {
        try {
            CloudBackupRunResultDto result = runGoogleDriveBackup();
            log.info("Initial Google Drive backup completed for tenant {}: file={}, remoteId={}",
                    tenantId, result.getFileName(), result.getRemoteId());
        } catch (Exception ex) {
            log.warn("Google Drive connected for tenant {}, but initial backup failed: {}",
                    tenantId, ex.getMessage());
        }
    }

    private void triggerInitialYandexDiskBackupSafely(String tenantId) {
        try {
            CloudBackupRunResultDto result = runYandexDiskBackup();
            log.info("Initial Yandex Disk backup completed for tenant {}: file={}, remotePath={}",
                    tenantId, result.getFileName(), result.getRemotePath());
        } catch (Exception ex) {
            log.warn("Yandex Disk connected for tenant {}, but initial backup failed: {}",
                    tenantId, ex.getMessage());
        }
    }

    private String normalizeBaseUrl(String url) {
        String value = trimToNull(url);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String buildApiPayWebhookUrl() {
        if (!StringUtils.hasText(integrationsPublicBaseUrl)) {
            return null;
        }
        return integrationsPublicBaseUrl.replaceAll("/+$", "") + "/internal/apipay/webhook";
    }

    private String generateWebhookSecret() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    private void ensureGoogleDriveOAuthConfigured() {
        if (!StringUtils.hasText(googleDriveOAuthClientId)
                || !StringUtils.hasText(googleDriveOAuthClientSecret)) {
            throw new BusinessException("GOOGLE_DRIVE_OAUTH_NOT_CONFIGURED",
                    "Google Drive OAuth client is not configured on server");
        }
    }

    private void ensureYandexDiskOAuthConfigured() {
        if (!StringUtils.hasText(yandexDiskOAuthClientId)
                || !StringUtils.hasText(yandexDiskOAuthClientSecret)) {
            throw new BusinessException("YANDEX_DISK_OAUTH_NOT_CONFIGURED",
                    "Yandex Disk OAuth client is not configured on server");
        }
    }

    private String requireTenantId() {
        String tenantId = resolveTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "Tenant context is required");
        }
        return tenantId.trim();
    }

    private String resolveTenantId() {
        String tenantId = trimToNull(TenantContext.getTenantId());
        if (StringUtils.hasText(tenantId)) {
            return tenantId;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            tenantId = trimToNull(jwtAuthenticationToken.getToken().getClaimAsString("tenant_id"));
            if (StringUtils.hasText(tenantId)) {
                return tenantId;
            }
        }

        try {
            ServletRequestAttributes requestAttributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                HttpServletRequest request = requestAttributes.getRequest();
                tenantId = trimToNull(request.getHeader("X-Tenant-ID"));
                if (StringUtils.hasText(tenantId)) {
                    return tenantId;
                }
            }
        } catch (Exception ignored) {
            // No web request bound to current thread.
        }

        return null;
    }

    private String buildGoogleDriveOAuthConnectUrlSafe() {
        try {
            String tenantId = resolveTenantId();
            if (!StringUtils.hasText(tenantId)
                    || !StringUtils.hasText(googleDriveOAuthClientId)
                    || !StringUtils.hasText(googleDriveOAuthClientSecret)) {
                return null;
            }
            return buildGoogleDriveOAuthConnectUrl(tenantId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildYandexDiskOAuthConnectUrlSafe() {
        try {
            String tenantId = resolveTenantId();
            if (!StringUtils.hasText(tenantId)
                    || !StringUtils.hasText(yandexDiskOAuthClientId)
                    || !StringUtils.hasText(yandexDiskOAuthClientSecret)) {
                return null;
            }
            return buildYandexDiskOAuthConnectUrl(tenantId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildGoogleDriveOAuthConnectUrl(String tenantId) {
        String state = buildOAuthState(OAUTH_PROVIDER_GOOGLE_DRIVE, tenantId);
        return UriComponentsBuilder
            .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleDriveOAuthClientId.trim())
                .queryParam("redirect_uri", buildGoogleDriveOAuthCallbackUrl())
                .queryParam("response_type", "code")
                .queryParam("scope", googleDriveOAuthScope)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    private String buildYandexDiskOAuthConnectUrl(String tenantId) {
        String state = buildOAuthState(OAUTH_PROVIDER_YANDEX_DISK, tenantId);
        return UriComponentsBuilder
            .fromUriString("https://oauth.yandex.ru/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", yandexDiskOAuthClientId.trim())
                .queryParam("redirect_uri", buildYandexDiskOAuthCallbackUrl())
                .queryParam("scope", yandexDiskOAuthScope)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    private String buildGoogleDriveOAuthCallbackUrl() {
        return integrationsPublicBaseUrl.replaceAll("/+$", "")
                + "/api/v1/settings/google-drive-backup/oauth/callback";
    }

    private String buildYandexDiskOAuthCallbackUrl() {
        return integrationsPublicBaseUrl.replaceAll("/+$", "")
                + "/api/v1/settings/yandex-disk-backup/oauth/callback";
    }

    private String buildOAuthState(String provider, String tenantId) {
        long issuedAt = Instant.now().getEpochSecond();
        String payload = provider + "|" + tenantId + "|" + issuedAt;
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = signStatePayload(payloadB64);
        return payloadB64 + "." + signature;
    }

    private OAuthStateData parseAndVerifyOAuthState(String state, String expectedProvider) {
        if (!StringUtils.hasText(state) || !state.contains(".")) {
            throw new BusinessException("OAUTH_STATE_INVALID", "OAuth state is invalid");
        }

        String[] parts = state.split("\\.", 2);
        if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
            throw new BusinessException("OAUTH_STATE_INVALID", "OAuth state is invalid");
        }

        String expectedSignature = signStatePayload(parts[0]);
        boolean validSignature = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8)
        );
        if (!validSignature) {
            throw new BusinessException("OAUTH_STATE_INVALID", "OAuth state signature is invalid");
        }

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("OAUTH_STATE_INVALID", "OAuth state payload is invalid");
        }

        String[] payloadParts = payload.split("\\|", 3);
        if (payloadParts.length != 3) {
            throw new BusinessException("OAUTH_STATE_INVALID", "OAuth state payload is invalid");
        }

        long issuedAt;
        try {
            issuedAt = Long.parseLong(payloadParts[2]);
        } catch (NumberFormatException e) {
            throw new BusinessException("OAUTH_STATE_INVALID", "OAuth state timestamp is invalid");
        }

        long ageSeconds = Instant.now().getEpochSecond() - issuedAt;
        if (ageSeconds < 0 || ageSeconds > 600) {
            throw new BusinessException("OAUTH_STATE_EXPIRED", "OAuth state is expired");
        }

        OAuthStateData stateData = new OAuthStateData(payloadParts[0], payloadParts[1], issuedAt);
        if (!expectedProvider.equals(stateData.provider())) {
            throw new BusinessException("OAUTH_STATE_PROVIDER_MISMATCH",
                    "OAuth state provider mismatch");
        }
        return stateData;
    }

    private String signStatePayload(String payloadB64) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(oauthStateSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new BusinessException("OAUTH_STATE_SIGN_FAILED", "Unable to sign OAuth state");
        }
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

    private record OAuthStateData(String provider, String tenantId, long issuedAtEpochSec) {
    }
}
