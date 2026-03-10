package com.ondeedu.tenant.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class CertIssuerClient {

    private final RestClient restClient;

    public CertIssuerClient(@Value("${cert.issuer.url:http://cert-issuer:8200}") String certIssuerUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(certIssuerUrl)
                .build();
    }

    @Async
    public void issueCert(String subdomain) {
        try {
            restClient.post()
                    .uri("/certs/{subdomain}", subdomain)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Cert issuance requested for subdomain: {}", subdomain);
        } catch (Exception e) {
            log.warn("Failed to request cert issuance for subdomain '{}': {}", subdomain, e.getMessage());
        }
    }

    @Async
    public void revokeCert(String subdomain) {
        try {
            restClient.delete()
                    .uri("/certs/{subdomain}", subdomain)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Cert revocation requested for subdomain: {}", subdomain);
        } catch (Exception e) {
            log.warn("Failed to request cert revocation for subdomain '{}': {}", subdomain, e.getMessage());
        }
    }
}
