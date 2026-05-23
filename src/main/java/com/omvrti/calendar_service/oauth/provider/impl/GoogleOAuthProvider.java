package com.omvrti.calendar_service.oauth.provider.impl;

import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.OAuthException;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Google OAuth Provider Implementation
 * Handles OAuth 2.0 flow for Google Calendar API
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthProvider implements IOAuthProvider {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${oauth.google.client-id:}")
    private String clientId;
    
    @Value("${oauth.google.client-secret:}")
    private String clientSecret;
    
    @Value("${oauth.google.redirect-uri:}")
    private String redirectUri;
    
    private static final String AUTHORIZE_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke";
    private static final String USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v2/userinfo";
    
    @Override
    public ProviderType getProviderType() {
        return ProviderType.GOOGLE;
    }
    
    @Override
    public String getAuthorizationUrl(String state, String redirectUri, String[] scopes) {
        log.debug("Generating authorization URL for Google");
        
        StringBuilder scopeBuilder = new StringBuilder();
        if (scopes != null && scopes.length > 0) {
            for (String scope : scopes) {
                scopeBuilder.append(scope).append(" ");
            }
        } else {
            scopeBuilder.append("https://www.googleapis.com/auth/calendar");
        }
        
        try {
            String encodedScope = URLEncoder.encode(scopeBuilder.toString().trim(), StandardCharsets.UTF_8);
            String encodedRedirectUri = URLEncoder.encode(redirectUri != null ? redirectUri : this.redirectUri, StandardCharsets.UTF_8);
            
            return AUTHORIZE_ENDPOINT +
                    "?client_id=" + clientId +
                    "&redirect_uri=" + encodedRedirectUri +
                    "&response_type=code" +
                    "&scope=" + encodedScope +
                    "&state=" + state +
                    "&access_type=offline" +
                    "&prompt=consent";
        } catch (Exception e) {
            throw new OAuthException("URL_ENCODING_FAILED", "Failed to encode authorization URL", "GOOGLE", e);
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

            JsonNode accessTokenNode = jsonNode.get("access_token");
            if (accessTokenNode == null || accessTokenNode.isNull()) {
                log.error("Google token response missing access_token field. Response keys: {}", jsonNode.fieldNames());
                throw new OAuthException("TOKEN_MISSING",
                        "access_token not present in Google token response", "GOOGLE");
            }
            String accessToken = accessTokenNode.asText();
            if (accessToken.isBlank() || "null".equals(accessToken)) {
                throw new OAuthException("TOKEN_INVALID",
                        "access_token is blank in Google token response", "GOOGLE");
            }

            return OAuthTokenDto.builder()
                    .accessToken(accessToken)
                    .refreshToken(jsonNode.has("refresh_token") ? jsonNode.get("refresh_token").asText() : null)
                    .idToken(jsonNode.has("id_token") ? jsonNode.get("id_token").asText() : null)
                    .expiresIn(jsonNode.get("expires_in").asLong())
                    .tokenType(jsonNode.has("token_type") ? jsonNode.get("token_type").asText() : "Bearer")
                    .scope(jsonNode.has("scope") ? jsonNode.get("scope").asText() : "https://www.googleapis.com/auth/calendar")
                    .issuedAt(java.time.LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Error during token exchange", e);
            throw new OAuthException("TOKEN_EXCHANGE_FAILED", "Error processing token response: " + e.getMessage(), "GOOGLE", e);
        }
    }
    
    @Override
    public OAuthTokenDto refreshAccessToken(String refreshToken) {
        log.info("Refreshing access token for Google");
        
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

            JsonNode accessTokenNode = jsonNode.get("access_token");
            if (accessTokenNode == null || accessTokenNode.isNull()) {
                log.error("Google refresh response missing access_token field. Response keys: {}", jsonNode.fieldNames());
                throw new OAuthException("TOKEN_MISSING",
                        "access_token not present in Google refresh token response", "GOOGLE");
            }
            String accessToken = accessTokenNode.asText();
            if (accessToken.isBlank() || "null".equals(accessToken)) {
                throw new OAuthException("TOKEN_INVALID",
                        "access_token is blank in Google refresh token response", "GOOGLE");
            }

            return OAuthTokenDto.builder()
                    .accessToken(accessToken)
                    .refreshToken(jsonNode.has("refresh_token") ? jsonNode.get("refresh_token").asText() : refreshToken)
                    .idToken(jsonNode.has("id_token") ? jsonNode.get("id_token").asText() : null)
                    .expiresIn(jsonNode.get("expires_in").asLong())
                    .tokenType(jsonNode.has("token_type") ? jsonNode.get("token_type").asText() : "Bearer")
                    .scope(jsonNode.has("scope") ? jsonNode.get("scope").asText() : "https://www.googleapis.com/auth/calendar")
                    .issuedAt(java.time.LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Error during token refresh", e);
            throw new OAuthException("REFRESH_FAILED", "Error processing refresh response: " + e.getMessage(), "GOOGLE", e);
        }
    }
    
    @Override
    public void revokeToken(String token) {
        log.info("Revoking token for Google");
        
        try {
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("token", token);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);
            restTemplate.postForEntity(REVOKE_ENDPOINT, request, String.class);
            
            log.info("Token revoked successfully");
        } catch (Exception e) {
            log.warn("Failed to revoke token from Google", e);
            // Don't throw exception - token revocation failure should not prevent logout
        }
    }
    
    @Override
    public String getUserEmail(OAuthTokenDto tokenDto) {
        log.debug("Fetching user email from Google");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tokenDto.getAccessToken());
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(USERINFO_ENDPOINT, HttpMethod.GET, request, String.class);
            
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new OAuthException("GET_USER_FAILED", "Failed to fetch user info: " + response.getStatusCode());
            }
            
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String email = jsonNode.get("email").asText();
            
            log.debug("User email fetched successfully: {}", email);
            return email;
        } catch (Exception e) {
            log.error("Error fetching user email", e);
            throw new OAuthException("GET_USER_FAILED", "Error processing user response: " + e.getMessage(), "GOOGLE", e);
        }
    }
}
