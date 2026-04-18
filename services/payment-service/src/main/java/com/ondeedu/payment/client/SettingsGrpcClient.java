package com.ondeedu.payment.client;

import com.ondeedu.common.payment.ApiPayRecipientField;
import com.ondeedu.common.payment.KpayRecipientField;
import com.ondeedu.grpc.settings.ApiPayConfigResponse;
import com.ondeedu.grpc.settings.GetApiPayConfigRequest;
import com.ondeedu.grpc.settings.GetKpayConfigRequest;
import com.ondeedu.grpc.settings.KpayConfigResponse;
import com.ondeedu.grpc.settings.SettingsServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class SettingsGrpcClient {

    @GrpcClient("settings-service")
    private SettingsServiceGrpc.SettingsServiceBlockingStub settingsStub;

    public record KpayConfigData(
            boolean enabled,
            boolean configured,
            String merchantId,
            String apiBaseUrl,
            KpayRecipientField recipientField,
            String apiKey,
            String apiSecret
    ) {
    }

        public record ApiPayConfigData(
            boolean enabled,
            boolean configured,
            String apiBaseUrl,
            ApiPayRecipientField recipientField,
            String apiKey,
            String webhookSecret
        ) {
        }

    public Optional<KpayConfigData> getKpayConfig() {
        try {
            KpayConfigResponse response = settingsStub.getKpayConfig(GetKpayConfigRequest.newBuilder().build());
            if (!response.getSuccess() || !response.hasConfig()) {
                return Optional.empty();
            }

            var config = response.getConfig();
            KpayRecipientField recipientField = KpayRecipientField.PARENT_PHONE;
            if (config.hasRecipientField() && config.getRecipientField().getValue() != null
                    && !config.getRecipientField().getValue().isBlank()) {
                recipientField = KpayRecipientField.valueOf(config.getRecipientField().getValue());
            }

            return Optional.of(new KpayConfigData(
                    config.getEnabled(),
                    config.getConfigured(),
                    config.hasMerchantId() ? blankToNull(config.getMerchantId().getValue()) : null,
                    config.hasApiBaseUrl() ? blankToNull(config.getApiBaseUrl().getValue()) : null,
                    recipientField,
                    config.hasApiKey() ? blankToNull(config.getApiKey().getValue()) : null,
                    config.hasApiSecret() ? blankToNull(config.getApiSecret().getValue()) : null
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid KPAY recipient field from settings-service: {}", e.getMessage());
            return Optional.empty();
        } catch (StatusRuntimeException e) {
            log.warn("Settings gRPC call failed: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Unexpected settings gRPC error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ApiPayConfigData> getApiPayConfig() {
        try {
            ApiPayConfigResponse response = settingsStub.getApiPayConfig(GetApiPayConfigRequest.newBuilder().build());
            if (!response.getSuccess() || !response.hasConfig()) {
                return Optional.empty();
            }

            var config = response.getConfig();
            ApiPayRecipientField recipientField = ApiPayRecipientField.PARENT_PHONE;
            if (config.hasRecipientField() && config.getRecipientField().getValue() != null
                    && !config.getRecipientField().getValue().isBlank()) {
                recipientField = ApiPayRecipientField.valueOf(config.getRecipientField().getValue());
            }

            return Optional.of(new ApiPayConfigData(
                    config.getEnabled(),
                    config.getConfigured(),
                    config.hasApiBaseUrl() ? blankToNull(config.getApiBaseUrl().getValue()) : null,
                    recipientField,
                    config.hasApiKey() ? blankToNull(config.getApiKey().getValue()) : null,
                    config.hasWebhookSecret() ? blankToNull(config.getWebhookSecret().getValue()) : null
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ApiPay recipient field from settings-service: {}", e.getMessage());
            return Optional.empty();
        } catch (StatusRuntimeException e) {
            log.warn("Settings gRPC call failed: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Unexpected settings gRPC error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
