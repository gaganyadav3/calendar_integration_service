package com.omvrti.calendar_service.oauth.provider.impl;

import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.OAuthException;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Zoho OAuth Provider Implementation
 * Handles OAuth 2.0 flow for Zoho APIs
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ZohoOAuthProvider implements IOAuthProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${oauth.zoho.client-id:}")
    private String clientId;

    @Value("${oauth.zoho.client-secret:}")
    private String clientSecret;

    @Value("${oauth.zoho.redirect-uri:}")
    private String redirectUri;

    private static final String AUTHORITY = "https://accounts.zoho.com";
    private static final String TOKEN_ENDPOINT = AUTHORITY + "/oauth/v2/token";
    private static final String AUTHORIZE_ENDPOINT = AUTHORITY + "/oauth/v2/auth";
    private static final String REVOKE_ENDPOINT = AUTHORITY + "/oauth/v2/token/revoke";
    private static final String ZOHO_API_BASE = "https://www.zohoapis.com";

    @Override
    public ProviderType getProviderType() {
        return ProviderType.ZOHO;
    }

    @Override
    public String getAuthorizationUrl(String state, String redirectUri, String[] scopes) {
        log.debug("Generating authorization URL for Zoho");

        StringBuilder scopeBuilder = new StringBuilder();
        if (scopes != null && scopes.length > 0) {
            for (String scope : scopes) {
                scopeBuilder.append(scope).append(",");
            }
        } else {
            scopeBuilder.append("ZohoCalendar.calendar.ALL");
        }

        try {
            String encodedScope = URLEncoder.encode(scopeBuilder.toString().replaceAll(",$", ""), StandardCharsets.UTF_8);
            String encodedRedirectUri = URLEncoder.encode(redirectUri != null ? redirectUri : this.redirectUri, StandardCharsets.UTF_8);

            return AUTHORIZE_ENDPOINT +
                    "?client_id=" + clientId +
                    "&redirect_uri=" + encodedRedirectUri +
                    "&response_type=code" +
                    "&scope=" + encodedScope +
                    "&state=" + state +
                    "&access_type=offline";
        } catch (Exception e) {
            throw new OAuthException("URL_ENCODING_FAILED", "Failed to encode authorization URL", "ZOHO", e);
        }
    }

    @Override
    public OAuthTokenDto exchangeCodeForToken(String code, String redirectUri) {
        log.info("Exchanging authorization code for access token");

        try {
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("client_id", clientId);
            requestBody.add("client_secret", clientSecret);
            requestBody.add("code", code);
            requestBody.add("redirect_uri", redirectUri != null ? redirectUri : this.redirectUri);
            requestBody.add("grant_type", "authorization_code");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(TOKEN_ENDPOINT, request, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new OAuthException("TOKEN_EXCHANGE_FAILED", "Failed to exchange code for token: " + response.getStatusCode());
            }

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            return OAuthTokenDto.builder()
                    .accessToken(jsonNode.get("access_token").asText())
                    .refreshToken(jsonNode.has("refresh_token") ? jsonNode.get("refresh_token").asText() : null)
                    .idToken(jsonNode.has("id_token") ? jsonNode.get("id_token").asText() : null)
                    .expiresIn(jsonNode.get("expires_in").asLong())
                    .tokenType(jsonNode.has("token_type") ? jsonNode.get("token_type").asText() : "Bearer")
                    .scope(jsonNode.has("scope") ? jsonNode.get("scope").asText() : "ZohoCalendar.calendar.ALL")
                    .issuedAt(java.time.LocalDateTime.now())
                    .build();
        } catch (RestClientException e) {
            log.error("Failed to exchange code for token", e);
            throw new OAuthException("TOKEN_EXCHANGE_FAILED", "Failed to contact token endpoint: " + e.getMessage(), "ZOHO", e);
        } catch (Exception e) {
            log.error("Error during token exchange", e);
            throw new OAuthException("TOKEN_EXCHANGE_FAILED", "Error processing token response: " + e.getMessage(), "ZOHO", e);
        }
    }

    @Override
    public OAuthTokenDto refreshAccessToken(String refreshToken) {
        log.info("Refreshing access token for Zoho");

        try {
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("client_id", clientId);
            requestBody.add("client_secret", clientSecret);
            requestBody.add("refresh_token", refreshToken);
            requestBody.add("grant_type", "refresh_token");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(TOKEN_ENDPOINT, request, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new OAuthException("REFRESH_FAILED", "Failed to refresh token: " + response.getStatusCode());
            }

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            return OAuthTokenDto.builder()
                    .accessToken(jsonNode.get("access_token").asText())
                    .refreshToken(refreshToken) // Zoho refresh tokens are long-lived
                    .idToken(jsonNode.has("id_token") ? jsonNode.get("id_token").asText() : null)
                    .expiresIn(jsonNode.get("expires_in").asLong())
                    .tokenType(jsonNode.has("token_type") ? jsonNode.get("token_type").asText() : "Bearer")
                    .scope(jsonNode.has("scope") ? jsonNode.get("scope").asText() : "ZohoCalendar.calendar.ALL")
                    .issuedAt(java.time.LocalDateTime.now())
                    .build();
        } catch (RestClientException e) {
            log.error("Failed to refresh token due to HTTP error", e);
            throw new OAuthException("REFRESH_FAILED", "Failed to contact token endpoint: " + e.getMessage(), "ZOHO", e);
        } catch (Exception e) {
            log.error("Error during token refresh", e);
            throw new OAuthException("REFRESH_FAILED", "Error processing refresh response: " + e.getMessage(), "ZOHO", e);
        }
    }

    @Override
    public void revokeToken(String token) {
        log.info("Revoking token for Zoho");

        try {
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("token", token);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);
            restTemplate.postForEntity(REVOKE_ENDPOINT, request, String.class);

            log.info("Token revoked successfully");
        } catch (Exception e) {
            log.warn("Failed to revoke token from Zoho", e);
            // Don't throw exception - token revocation failure should not prevent logout
        }
    }

    @Override
    public String getUserEmail(OAuthTokenDto tokenDto) {
        log.debug("Fetching user email from Zoho");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tokenDto.getAccessToken());
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(ZOHO_API_BASE + "/crm/v2/users?type=CurrentUser", HttpMethod.GET, request, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new OAuthException("GET_USER_FAILED", "Failed to fetch user info: " + response.getStatusCode());
            }

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            JsonNode users = jsonNode.get("users");
            if (users != null && users.isArray() && users.size() > 0) {
                return users.get(0).get("email").asText();
            }
            throw new OAuthException("GET_USER_FAILED", "No user email found");
        } catch (RestClientException e) {
            log.error("Failed to fetch user email due to HTTP error", e);
            throw new OAuthException("GET_USER_FAILED", "Failed to contact Zoho API: " + e.getMessage(), "ZOHO", e);
        } catch (Exception e) {
            log.error("Error fetching user email", e);
            throw new OAuthException("GET_USER_FAILED", "Error processing user response: " + e.getMessage(), "ZOHO", e);
        }
    }
}
