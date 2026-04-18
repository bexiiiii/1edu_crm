package com.ondeedu.settings.grpc;

import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.ondeedu.common.payment.ApiPayRecipientField;
import com.ondeedu.common.payment.KpayRecipientField;
import com.ondeedu.grpc.settings.*;
import com.ondeedu.settings.dto.SettingsDto;
import com.ondeedu.settings.service.SettingsService;
import com.ondeedu.common.util.GrpcUtils;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalTime;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class SettingsGrpcService extends SettingsServiceGrpc.SettingsServiceImplBase {

    private final SettingsService settingsService;

    @Override
    public void getSettings(GetSettingsRequest request, StreamObserver<SettingsResponse> observer) {
        try {
            SettingsDto dto = settingsService.getSettings();
            observer.onNext(buildResponse(true, null, dto));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getSettings error", e);
            observer.onNext(SettingsResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void updateSettings(UpdateSettingsRequest request, StreamObserver<SettingsResponse> observer) {
        try {
            com.ondeedu.settings.dto.UpdateSettingsRequest dto =
                com.ondeedu.settings.dto.UpdateSettingsRequest.builder()
                    .centerName(request.hasCenterName() ? request.getCenterName().getValue() : null)
                    .mainDirection(request.hasMainDirection() ? request.getMainDirection().getValue() : null)
                    .directorName(request.hasDirectorName() ? request.getDirectorName().getValue() : null)
                    .corporateEmail(request.hasCorporateEmail() ? request.getCorporateEmail().getValue() : null)
                    .city(request.hasCity() ? request.getCity().getValue() : null)
                    .workPhone(request.hasWorkPhone() ? request.getWorkPhone().getValue() : null)
                    .address(request.hasAddress() ? request.getAddress().getValue() : null)
                    .timezone(request.hasTimezone() ? request.getTimezone().getValue() : null)
                    .currency(request.hasCurrency() ? request.getCurrency().getValue() : null)
                    .language(request.hasLanguage() ? request.getLanguage().getValue() : null)
                    .workingHoursStart(request.hasWorkingHoursStart()
                        ? LocalTime.parse(request.getWorkingHoursStart().getValue()) : null)
                    .workingHoursEnd(request.hasWorkingHoursEnd()
                        ? LocalTime.parse(request.getWorkingHoursEnd().getValue()) : null)
                    .defaultLessonDurationMin(request.hasDefaultLessonDurationMin()
                        ? request.getDefaultLessonDurationMin().getValue() : null)
                    .trialLessonDurationMin(request.hasTrialLessonDurationMin()
                        ? request.getTrialLessonDurationMin().getValue() : null)
                    .maxGroupSize(request.hasMaxGroupSize() ? request.getMaxGroupSize().getValue() : null)
                    .autoMarkAttendance(request.hasAutoMarkAttendance()
                        ? request.getAutoMarkAttendance().getValue() : null)
                    .attendanceWindowDays(request.hasAttendanceWindowDays()
                        ? request.getAttendanceWindowDays().getValue() : null)
                    .smsEnabled(request.hasSmsEnabled() ? request.getSmsEnabled().getValue() : null)
                    .emailEnabled(request.hasEmailEnabled() ? request.getEmailEnabled().getValue() : null)
                    .smsSenderName(request.hasSmsSenderName() ? request.getSmsSenderName().getValue() : null)
                    .latePaymentReminderDays(request.hasLatePaymentReminderDays()
                        ? request.getLatePaymentReminderDays().getValue() : null)
                    .subscriptionExpiryReminderDays(request.hasSubscriptionExpiryReminderDays()
                        ? request.getSubscriptionExpiryReminderDays().getValue() : null)
                    .brandColor(request.hasBrandColor() ? request.getBrandColor().getValue() : null)
                    .build();

            SettingsDto updated = settingsService.upsertSettings(dto);
            observer.onNext(buildResponse(true, "Settings updated", updated));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC updateSettings error", e);
            observer.onNext(SettingsResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getKpayConfig(GetKpayConfigRequest request, StreamObserver<KpayConfigResponse> observer) {
        try {
            var dto = settingsService.getInternalKpayConfig();

            KpayConfig.Builder configBuilder = KpayConfig.newBuilder()
                    .setEnabled(dto.isEnabled())
                    .setConfigured(dto.isEnabled()
                            && dto.getApiKey() != null
                            && !dto.getApiKey().isBlank()
                            && dto.getApiSecret() != null
                            && !dto.getApiSecret().isBlank());

            if (dto.getMerchantId() != null) {
                configBuilder.setMerchantId(StringValue.of(dto.getMerchantId()));
            }
            if (dto.getApiBaseUrl() != null) {
                configBuilder.setApiBaseUrl(StringValue.of(dto.getApiBaseUrl()));
            }

            KpayRecipientField recipientField = dto.getRecipientField() != null
                    ? dto.getRecipientField()
                    : KpayRecipientField.PARENT_PHONE;
            configBuilder.setRecipientField(StringValue.of(recipientField.name()));

            if (dto.getApiKey() != null) {
                configBuilder.setApiKey(StringValue.of(dto.getApiKey()));
            }
            if (dto.getApiSecret() != null) {
                configBuilder.setApiSecret(StringValue.of(dto.getApiSecret()));
            }

            observer.onNext(KpayConfigResponse.newBuilder()
                    .setSuccess(true)
                    .setConfig(configBuilder.build())
                    .build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getKpayConfig error", e);
            observer.onNext(KpayConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(StringValue.of(e.getMessage()))
                    .build());
            observer.onCompleted();
        }
    }

    @Override
    public void getApiPayConfig(GetApiPayConfigRequest request, StreamObserver<ApiPayConfigResponse> observer) {
        try {
            var dto = settingsService.getInternalApiPayConfig();

            ApiPayConfig.Builder configBuilder = ApiPayConfig.newBuilder()
                    .setEnabled(dto.isEnabled())
                    .setConfigured(dto.isEnabled()
                            && dto.getApiKey() != null
                            && !dto.getApiKey().isBlank()
                            && dto.getWebhookSecret() != null
                            && !dto.getWebhookSecret().isBlank());

            if (dto.getApiBaseUrl() != null) {
                configBuilder.setApiBaseUrl(StringValue.of(dto.getApiBaseUrl()));
            }

            ApiPayRecipientField recipientField = dto.getRecipientField() != null
                    ? dto.getRecipientField()
                    : ApiPayRecipientField.PARENT_PHONE;
            configBuilder.setRecipientField(StringValue.of(recipientField.name()));

            if (dto.getApiKey() != null) {
                configBuilder.setApiKey(StringValue.of(dto.getApiKey()));
            }

            if (dto.getWebhookSecret() != null) {
                configBuilder.setWebhookSecret(StringValue.of(dto.getWebhookSecret()));
            }

            observer.onNext(ApiPayConfigResponse.newBuilder()
                    .setSuccess(true)
                    .setConfig(configBuilder.build())
                    .build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getApiPayConfig error", e);
            observer.onNext(ApiPayConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(StringValue.of(e.getMessage()))
                    .build());
            observer.onCompleted();
        }
    }

    @Override
    public void getAisarConfig(GetAisarConfigRequest request, StreamObserver<AisarConfigResponse> observer) {
        try {
            var dto = settingsService.getInternalAisarConfig();

            AisarConfig.Builder configBuilder = AisarConfig.newBuilder()
                    .setEnabled(dto.isEnabled())
                    .setConfigured(dto.isEnabled()
                            && dto.getWebhookSecret() != null
                            && !dto.getWebhookSecret().isBlank());

            if (dto.getApiBaseUrl() != null) {
                configBuilder.setApiBaseUrl(StringValue.of(dto.getApiBaseUrl()));
            }
            if (dto.getApiKey() != null) {
                configBuilder.setApiKey(StringValue.of(dto.getApiKey()));
            }
            if (dto.getWebhookSecret() != null) {
                configBuilder.setWebhookSecret(StringValue.of(dto.getWebhookSecret()));
            }

            observer.onNext(AisarConfigResponse.newBuilder()
                    .setSuccess(true)
                    .setConfig(configBuilder.build())
                    .build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getAisarConfig error", e);
            observer.onNext(AisarConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(StringValue.of(e.getMessage()))
                    .build());
            observer.onCompleted();
        }
    }

    @Override
    public void getFtelecomConfig(GetFtelecomConfigRequest request, StreamObserver<FtelecomConfigResponse> observer) {
        try {
            var dto = settingsService.getInternalFtelecomConfig();

            FtelecomConfig.Builder configBuilder = FtelecomConfig.newBuilder()
                    .setEnabled(dto.isEnabled())
                    .setConfigured(dto.isEnabled()
                            && dto.getCrmToken() != null
                            && !dto.getCrmToken().isBlank());

            if (dto.getApiBaseUrl() != null) {
                configBuilder.setApiBaseUrl(StringValue.of(dto.getApiBaseUrl()));
            }
            if (dto.getCrmToken() != null) {
                configBuilder.setCrmToken(StringValue.of(dto.getCrmToken()));
            }

            observer.onNext(FtelecomConfigResponse.newBuilder()
                    .setSuccess(true)
                    .setConfig(configBuilder.build())
                    .build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getFtelecomConfig error", e);
            observer.onNext(FtelecomConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(StringValue.of(e.getMessage()))
                    .build());
            observer.onCompleted();
        }
    }

    @Override
    public void getZadarmaConfig(GetZadarmaConfigRequest request, StreamObserver<ZadarmaConfigResponse> observer) {
        try {
            var dto = settingsService.getInternalZadarmaConfig();

            ZadarmaConfig.Builder configBuilder = ZadarmaConfig.newBuilder()
                    .setEnabled(dto.isEnabled())
                    .setConfigured(dto.isEnabled()
                            && dto.getUserKey() != null
                            && !dto.getUserKey().isBlank()
                            && dto.getUserSecret() != null
                            && !dto.getUserSecret().isBlank());

            if (dto.getApiBaseUrl() != null) {
                configBuilder.setApiBaseUrl(StringValue.of(dto.getApiBaseUrl()));
            }
            if (dto.getUserKey() != null) {
                configBuilder.setUserKey(StringValue.of(dto.getUserKey()));
            }
            if (dto.getUserSecret() != null) {
                configBuilder.setUserSecret(StringValue.of(dto.getUserSecret()));
            }

            observer.onNext(ZadarmaConfigResponse.newBuilder()
                    .setSuccess(true)
                    .setConfig(configBuilder.build())
                    .build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getZadarmaConfig error", e);
            observer.onNext(ZadarmaConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(StringValue.of(e.getMessage()))
                    .build());
            observer.onCompleted();
        }
    }

    private SettingsResponse buildResponse(boolean success, String message, SettingsDto dto) {
        SettingsResponse.Builder builder = SettingsResponse.newBuilder().setSuccess(success);
        if (message != null) builder.setMessage(StringValue.of(message));
        if (dto != null) builder.setSettings(toGrpcSettings(dto));
        return builder.build();
    }

    private Settings toGrpcSettings(SettingsDto dto) {
        Settings.Builder builder = Settings.newBuilder();

        if (dto.getId() != null) builder.setId(StringValue.of(dto.getId().toString()));
        if (dto.getCenterName() != null) builder.setCenterName(StringValue.of(dto.getCenterName()));
        if (dto.getMainDirection() != null) builder.setMainDirection(StringValue.of(dto.getMainDirection()));
        if (dto.getDirectorName() != null) builder.setDirectorName(StringValue.of(dto.getDirectorName()));
        if (dto.getCorporateEmail() != null) builder.setCorporateEmail(StringValue.of(dto.getCorporateEmail()));
        if (dto.getCity() != null) builder.setCity(StringValue.of(dto.getCity()));
        if (dto.getWorkPhone() != null) builder.setWorkPhone(StringValue.of(dto.getWorkPhone()));
        if (dto.getAddress() != null) builder.setAddress(StringValue.of(dto.getAddress()));
        if (dto.getTimezone() != null) builder.setTimezone(StringValue.of(dto.getTimezone()));
        if (dto.getCurrency() != null) builder.setCurrency(StringValue.of(dto.getCurrency()));
        if (dto.getLanguage() != null) builder.setLanguage(StringValue.of(dto.getLanguage()));
        if (dto.getWorkingHoursStart() != null)
            builder.setWorkingHoursStart(StringValue.of(dto.getWorkingHoursStart().toString()));
        if (dto.getWorkingHoursEnd() != null)
            builder.setWorkingHoursEnd(StringValue.of(dto.getWorkingHoursEnd().toString()));
        if (dto.getDefaultLessonDurationMin() != null)
            builder.setDefaultLessonDurationMin(Int32Value.of(dto.getDefaultLessonDurationMin()));
        if (dto.getTrialLessonDurationMin() != null)
            builder.setTrialLessonDurationMin(Int32Value.of(dto.getTrialLessonDurationMin()));
        if (dto.getMaxGroupSize() != null) builder.setMaxGroupSize(Int32Value.of(dto.getMaxGroupSize()));
        if (dto.getAutoMarkAttendance() != null)
            builder.setAutoMarkAttendance(dto.getAutoMarkAttendance());
        if (dto.getAttendanceWindowDays() != null)
            builder.setAttendanceWindowDays(Int32Value.of(dto.getAttendanceWindowDays()));
        if (dto.getSmsEnabled() != null) builder.setSmsEnabled(dto.getSmsEnabled());
        if (dto.getEmailEnabled() != null) builder.setEmailEnabled(dto.getEmailEnabled());
        if (dto.getSmsSenderName() != null) builder.setSmsSenderName(StringValue.of(dto.getSmsSenderName()));
        if (dto.getLatePaymentReminderDays() != null)
            builder.setLatePaymentReminderDays(Int32Value.of(dto.getLatePaymentReminderDays()));
        if (dto.getSubscriptionExpiryReminderDays() != null)
            builder.setSubscriptionExpiryReminderDays(Int32Value.of(dto.getSubscriptionExpiryReminderDays()));
        if (dto.getBrandColor() != null) builder.setBrandColor(StringValue.of(dto.getBrandColor()));
        if (dto.getCreatedAt() != null) builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        if (dto.getUpdatedAt() != null) builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));

        return builder.build();
    }
}
