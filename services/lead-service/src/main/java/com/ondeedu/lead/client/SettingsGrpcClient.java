package com.ondeedu.lead.client;

import com.ondeedu.grpc.settings.AisarConfigResponse;
import com.ondeedu.grpc.settings.FtelecomConfigResponse;
import com.ondeedu.grpc.settings.GetAisarConfigRequest;
import com.ondeedu.grpc.settings.GetFtelecomConfigRequest;
import com.ondeedu.grpc.settings.GetZadarmaConfigRequest;
import com.ondeedu.grpc.settings.SettingsServiceGrpc;
import com.ondeedu.grpc.settings.ZadarmaConfigResponse;
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

    public record AisarConfigData(
            boolean enabled,
            boolean configured,
            String apiBaseUrl,
            String apiKey,
            String webhookSecret
    ) {
    }

    public record FtelecomConfigData(
            boolean enabled,
            boolean configured,
            String apiBaseUrl,
            String crmToken
    ) {
    }

    public record ZadarmaConfigData(
            boolean enabled,
            boolean configured,
            String apiBaseUrl,
            String userKey,
            String userSecret
    ) {
    }

    public Optional<AisarConfigData> getAisarConfig() {
        try {
            AisarConfigResponse response = settingsStub.getAisarConfig(GetAisarConfigRequest.newBuilder().build());
            if (!response.getSuccess() || !response.hasConfig()) {
                return Optional.empty();
            }

            var config = response.getConfig();
            return Optional.of(new AisarConfigData(
                    config.getEnabled(),
                    config.getConfigured(),
                    config.hasApiBaseUrl() ? blankToNull(config.getApiBaseUrl().getValue()) : null,
                    config.hasApiKey() ? blankToNull(config.getApiKey().getValue()) : null,
                    config.hasWebhookSecret() ? blankToNull(config.getWebhookSecret().getValue()) : null
            ));
        } catch (StatusRuntimeException e) {
            log.warn("AISAR settings gRPC call failed: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Unexpected AISAR settings gRPC error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<FtelecomConfigData> getFtelecomConfig() {
        try {
            FtelecomConfigResponse response = settingsStub.getFtelecomConfig(GetFtelecomConfigRequest.newBuilder().build());
            if (!response.getSuccess() || !response.hasConfig()) {
                return Optional.empty();
            }

            var config = response.getConfig();
            return Optional.of(new FtelecomConfigData(
                    config.getEnabled(),
                    config.getConfigured(),
                    config.hasApiBaseUrl() ? blankToNull(config.getApiBaseUrl().getValue()) : null,
                    config.hasCrmToken() ? blankToNull(config.getCrmToken().getValue()) : null
            ));
        } catch (StatusRuntimeException e) {
            log.warn("Freedom Telecom settings gRPC call failed: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Unexpected Freedom Telecom settings gRPC error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ZadarmaConfigData> getZadarmaConfig() {
        try {
            ZadarmaConfigResponse response = settingsStub.getZadarmaConfig(GetZadarmaConfigRequest.newBuilder().build());
            if (!response.getSuccess() || !response.hasConfig()) {
                return Optional.empty();
            }

            var config = response.getConfig();
            return Optional.of(new ZadarmaConfigData(
                    config.getEnabled(),
                    config.getConfigured(),
                    config.hasApiBaseUrl() ? blankToNull(config.getApiBaseUrl().getValue()) : null,
                    config.hasUserKey() ? blankToNull(config.getUserKey().getValue()) : null,
                    config.hasUserSecret() ? blankToNull(config.getUserSecret().getValue()) : null
            ));
        } catch (StatusRuntimeException e) {
            log.warn("Zadarma settings gRPC call failed: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Unexpected Zadarma settings gRPC error: {}", e.getMessage());
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
