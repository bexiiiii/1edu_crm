package com.ondeedu.lead.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.lead.client.SettingsGrpcClient;
import com.ondeedu.lead.dto.CreateLeadRequest;
import com.ondeedu.lead.entity.Lead;
import com.ondeedu.lead.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZadarmaWebhookServiceTest {

    private static final String SECRET = "test_secret";
    private static final String TENANT_ID = "tenant-123";

    @Mock
    private SettingsGrpcClient settingsGrpcClient;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadService leadService;

    @Captor
    private ArgumentCaptor<CreateLeadRequest> leadRequestCaptor;

    private ZadarmaWebhookService zadarmaWebhookService;

    @BeforeEach
    void setUp() {
        zadarmaWebhookService = new ZadarmaWebhookService(
                settingsGrpcClient,
                leadRepository,
                leadService
        );
    }

    @Test
    void handleWebhook_createsLeadForIncomingCall() throws Exception {
        Map<String, String> payload = Map.of(
                "event", "NOTIFY_END",
                "caller_id", "+7 (777) 123-45-67",
                "called_did", "+77270001122",
                "call_start", "1713438302",
                "pbx_call_id", "pbx-call-1",
                "duration", "37",
                "disposition", "answered"
        );

        when(settingsGrpcClient.getZadarmaConfig()).thenReturn(Optional.of(
                new SettingsGrpcClient.ZadarmaConfigData(true, true, "https://api.zadarma.com", "key", SECRET)
        ));
        when(leadRepository.findLatestByNormalizedPhone("+77771234567")).thenReturn(Optional.empty());

        zadarmaWebhookService.handleWebhook(TENANT_ID, payload, signature(payload, SECRET));

        verify(leadService).createLead(leadRequestCaptor.capture());
        CreateLeadRequest request = leadRequestCaptor.getValue();

        assertEquals("+77771234567", request.getFirstName());
        assertEquals("Zadarma", request.getLastName());
        assertEquals("+77771234567", request.getPhone());
        assertEquals("ZADARMA", request.getSource());
        assertTrue(request.getNotes().contains("pbx-call-1"));
        assertTrue(request.getNotes().contains("answered"));
    }

    @Test
    void handleWebhook_skipsDuplicateLeadByPhone() throws Exception {
        Map<String, String> payload = Map.of(
                "event", "NOTIFY_INTERNAL",
                "caller_id", "+7 777 123 45 67",
                "called_did", "+77270001122",
                "call_start", "1713438302"
        );

        when(settingsGrpcClient.getZadarmaConfig()).thenReturn(Optional.of(
                new SettingsGrpcClient.ZadarmaConfigData(true, true, "https://api.zadarma.com", "key", SECRET)
        ));
        when(leadRepository.findLatestByNormalizedPhone("+77771234567")).thenReturn(Optional.of(new Lead()));

        zadarmaWebhookService.handleWebhook(TENANT_ID, payload, signature(payload, SECRET));

        verify(leadService, never()).createLead(any());
    }

    @Test
    void handleWebhook_rejectsInvalidSignature() {
        Map<String, String> payload = Map.of(
                "event", "NOTIFY_END",
                "caller_id", "+7 777 123 45 67",
                "called_did", "+77270001122",
                "call_start", "1713438302"
        );

        when(settingsGrpcClient.getZadarmaConfig()).thenReturn(Optional.of(
                new SettingsGrpcClient.ZadarmaConfigData(true, true, "https://api.zadarma.com", "key", SECRET)
        ));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> zadarmaWebhookService.handleWebhook(TENANT_ID, payload, "bad-signature"));

        assertEquals("ZADARMA_SIGNATURE_INVALID", exception.getErrorCode());
        verify(leadService, never()).createLead(any());
    }

    private String signature(Map<String, String> payload, String secret) throws Exception {
        String source = payload.get("caller_id") + payload.get("called_did") + payload.get("call_start");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(source.getBytes(StandardCharsets.UTF_8)));
    }
}
