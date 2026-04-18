package com.ondeedu.lead.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AisarWebhookServiceTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String TENANT_ID = "tenant-123";

    @Mock
    private SettingsGrpcClient settingsGrpcClient;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadService leadService;

    @Captor
    private ArgumentCaptor<CreateLeadRequest> leadRequestCaptor;

    private AisarWebhookService aisarWebhookService;

    @BeforeEach
    void setUp() {
        aisarWebhookService = new AisarWebhookService(
                settingsGrpcClient,
                leadRepository,
                leadService,
                new ObjectMapper()
        );
    }

    @Test
    void handleWebhook_createsLeadForNewContact() throws Exception {
        String payload = """
                {
                  "event": "message.created",
                  "contact": {
                    "id": "contact-1",
                    "name": "Aliya Nur",
                    "phone": "+7 777 123 45 67",
                    "email": "Aliya@example.com"
                  },
                  "conversation": {
                    "id": "conversation-42"
                  },
                  "channel": {
                    "type": "WHATSAPP"
                  },
                  "message": {
                    "text": "Здравствуйте"
                  }
                }
                """;

        when(settingsGrpcClient.getAisarConfig()).thenReturn(Optional.of(
                new SettingsGrpcClient.AisarConfigData(true, true, "https://aisar.app", null, SECRET)
        ));
        when(leadRepository.findLatestByNormalizedPhone("+77771234567")).thenReturn(Optional.empty());
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("aliya@example.com")).thenReturn(Optional.empty());

        aisarWebhookService.handleWebhook(TENANT_ID, payload, "sha256=" + signatureHex(payload, SECRET));

        verify(leadService).createLead(leadRequestCaptor.capture());
        CreateLeadRequest request = leadRequestCaptor.getValue();

        assertEquals("Aliya", request.getFirstName());
        assertEquals("Nur", request.getLastName());
        assertEquals("+77771234567", request.getPhone());
        assertEquals("aliya@example.com", request.getEmail());
        assertEquals("AISAR", request.getSource());
        assertTrue(request.getNotes().contains("conversation-42"));
        assertTrue(request.getNotes().contains("Здравствуйте"));
    }

    @Test
    void handleWebhook_skipsDuplicateLeadByPhone() throws Exception {
        String payload = """
                {
                  "event": "conversation.created",
                  "contact": {
                    "name": "Dana",
                    "phone": "+7 (777) 123-45-67"
                  }
                }
                """;

        when(settingsGrpcClient.getAisarConfig()).thenReturn(Optional.of(
                new SettingsGrpcClient.AisarConfigData(true, true, "https://aisar.app", null, SECRET)
        ));
        when(leadRepository.findLatestByNormalizedPhone("+77771234567")).thenReturn(Optional.of(new Lead()));

        aisarWebhookService.handleWebhook(TENANT_ID, payload, signatureHex(payload, SECRET));

        verify(leadService, never()).createLead(any());
    }

    @Test
    void handleWebhook_rejectsInvalidSignature() {
        String payload = """
                {
                  "event": "message.created",
                  "contact": {
                    "name": "Aliya",
                    "phone": "+77771234567"
                  }
                }
                """;

        when(settingsGrpcClient.getAisarConfig()).thenReturn(Optional.of(
                new SettingsGrpcClient.AisarConfigData(true, true, "https://aisar.app", null, SECRET)
        ));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> aisarWebhookService.handleWebhook(TENANT_ID, payload, "invalid-signature"));

        assertEquals("AISAR_SIGNATURE_INVALID", exception.getErrorCode());
        verify(leadService, never()).createLead(any());
    }

    private String signatureHex(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
