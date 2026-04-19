package com.ondeedu.settings.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondeedu.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleDriveOAuthClient {

    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://oauth2.googleapis.com")
            .build();

    public record TokenResult(String accessToken) {
    }

    public TokenResult exchangeAuthorizationCode(String code,
                                                 String clientId,
                                                 String clientSecret,
                                                 String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);

        try {
            String responseBody = restClient.post()
                    .uri("/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new BusinessException("GOOGLE_DRIVE_OAUTH_EXCHANGE_FAILED",
                                "Google Drive OAuth exchange failed with status " + response.getStatusCode());
                    })
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String accessToken = textOrNull(root, "access_token");
            if (!StringUtils.hasText(accessToken)) {
                throw new BusinessException("GOOGLE_DRIVE_OAUTH_EXCHANGE_FAILED",
                        "Google Drive OAuth did not return access_token");
            }
            return new TokenResult(accessToken.trim());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to exchange Google Drive OAuth code", e);
            throw new BusinessException("GOOGLE_DRIVE_OAUTH_EXCHANGE_FAILED",
                    "Failed to exchange Google Drive OAuth code: " + e.getMessage());
        }
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode child = node.path(fieldName);
        return child.isMissingNode() || child.isNull() ? null : child.asText(null);
    }
}
