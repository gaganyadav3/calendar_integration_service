package com.omvrti.calendar_service.oauth.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.OAuthException;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutlookOAuthProvider implements IOAuthProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${oauth.outlook.client-id:}")
    private String clientId;

    @Value("${oauth.outlook.client-secret:}")
    private String clientSecret;

    @Value("${oauth.outlook.redirect-uri:}")
    private String redirectUri;

    private static final String AUTHORITY = "https://login.microsoftonline.com/common";
    private static final String TOKEN_ENDPOINT = AUTHORITY + "/oauth2/v2.0/token";
    private static final String AUTHORIZE_ENDPOINT = AUTHORITY + "/oauth2/v2.0/authorize";
    private static final String GRAPH_API_ME = "https://graph.microsoft.com/v1.0/me";

    // ✅ FIXED SCOPE (VERY IMPORTANT)
    private static final String DEFAULT_SCOPE =
            "openid profile email offline_access User.Read Calendars.ReadWrite";

    @Override
    public ProviderType getProviderType() {
        return ProviderType.OUTLOOK;
    }

    @Override
    public String getAuthorizationUrl(String state, String redirectUri, String[] scopes) {
        log.debug("Generating authorization URL for Outlook");

        try {

            String finalScope = "openid profile email offline_access User.Read Calendars.ReadWrite";

            log.info("Final scope for authorization: {}", finalScope);

            String encodedScope = URLEncoder.encode(finalScope, StandardCharsets.UTF_8);
            String encodedRedirectUri = URLEncoder.encode(
                    redirectUri != null ? redirectUri : this.redirectUri,
                    StandardCharsets.UTF_8
            );

            return AUTHORIZE_ENDPOINT +
                    "?client_id=" + clientId +
                    "&redirect_uri=" + encodedRedirectUri +
                    "&response_type=code" +
                    "&scope=" + encodedScope +
                    "&response_mode=query" +
                    "&prompt=consent" +
                    "&state=" + state;

        } catch (Exception e) {
            throw new OAuthException("URL_ENCODING_FAILED",
                    "Failed to encode authorization URL",
                    "OUTLOOK", e);
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
            //requestBody.add("scope", DEFAULT_SCOPE);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request =
                    new HttpEntity<>(requestBody, headers);

//            log.debug("Token exchange request: client_id={}, code={}, redirect_uri={}, grant_type={}",
//                    clientId, code, redirectUri != null ? redirectUri : this.redirectUri, "authorization_code");

            ResponseEntity<String> response =
                    restTemplate.postForEntity(TOKEN_ENDPOINT, request, String.class);

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            return buildTokenDto(jsonNode);

        } catch (RestClientException e) {
            log.error("Token exchange failed", e);
            if (e instanceof org.springframework.web.client.HttpStatusCodeException) {
                org.springframework.web.client.HttpStatusCodeException hsce = (org.springframework.web.client.HttpStatusCodeException) e;
                log.error("Microsoft response error: status={}, body={}", hsce.getStatusCode(), hsce.getResponseBodyAsString());
            }
            throw new OAuthException("TOKEN_EXCHANGE_FAILED",
                    "Failed to contact token endpoint: " + e.getMessage(),
                    "OUTLOOK", e);
        } catch (Exception e) {
            log.error("Token processing error", e);
            throw new OAuthException("TOKEN_EXCHANGE_FAILED",
                    "Error processing token response: " + e.getMessage(),
                    "OUTLOOK", e);
        }
    }

    @Override
    public OAuthTokenDto refreshAccessToken(String refreshToken) {
        log.info("Refreshing access token for Outlook");

        try {
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("client_id", clientId);
            requestBody.add("client_secret", clientSecret);
            requestBody.add("refresh_token", refreshToken);
            requestBody.add("grant_type", "refresh_token");
            requestBody.add("scope", DEFAULT_SCOPE); // ✅ FIXED

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(TOKEN_ENDPOINT, request, String.class);

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            return buildTokenDto(jsonNode);

        } catch (RestClientException e) {
            log.error("Refresh token failed", e);
            throw new OAuthException("REFRESH_FAILED",
                    "Failed to contact token endpoint: " + e.getMessage(),
                    "OUTLOOK", e);
        } catch (Exception e) {
            log.error("Refresh processing error", e);
            throw new OAuthException("REFRESH_FAILED",
                    "Error processing refresh response: " + e.getMessage(),
                    "OUTLOOK", e);
        }
    }

    @Override
    public void revokeToken(String token) {

    }

    @Override
    public String getUserEmail(OAuthTokenDto tokenDto) {
        log.debug("Fetching user email from Outlook");

        if (tokenDto.getIdToken() != null) {
            try {
                // Parse ID token to get email
                String email = parseEmailFromIdToken(tokenDto.getIdToken());
                if (email != null) {
                    log.debug("User email fetched from ID token: {}", email);
                    return email;
                }
            } catch (Exception e) {
                log.warn("Failed to parse email from ID token, falling back to Graph API", e);
            }
        }

        // Fallback to Graph API /me endpoint
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tokenDto.getAccessToken());
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPH_API_ME,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            String email = jsonNode.has("mail") && !jsonNode.get("mail").asText().isEmpty()
                    ? jsonNode.get("mail").asText()
                    : jsonNode.get("userPrincipalName").asText();

            log.debug("User email fetched successfully from Graph API: {}", email);
            return email;

        } catch (RestClientException e) {
            log.error("Graph API call failed (likely scope issue)", e);
            throw new OAuthException("GET_USER_FAILED",
                    "Graph API unauthorized. Check scopes (User.Read)",
                    "OUTLOOK", e);
        } catch (Exception e) {
            log.error("User parsing failed", e);
            throw new OAuthException("GET_USER_FAILED",
                    "Error processing user response",
                    "OUTLOOK", e);
        }
    }

    private OAuthTokenDto buildTokenDto(JsonNode jsonNode) {
        JsonNode accessTokenNode = jsonNode.get("access_token");
        if (accessTokenNode == null || accessTokenNode.isNull()) {
            log.error("Outlook token response missing access_token field. Response keys: {}", jsonNode.fieldNames());
            throw new OAuthException("TOKEN_MISSING",
                    "access_token not present in Outlook token response", "OUTLOOK");
        }
        String accessToken = accessTokenNode.asText();
        if (accessToken.isBlank() || "null".equals(accessToken)) {
            throw new OAuthException("TOKEN_INVALID",
                    "access_token is blank in Outlook token response", "OUTLOOK");
        }
        return OAuthTokenDto.builder()
                .accessToken(accessToken)
                .refreshToken(jsonNode.has("refresh_token") ? jsonNode.get("refresh_token").asText() : null)
                .idToken(jsonNode.has("id_token") ? jsonNode.get("id_token").asText() : null)
                .expiresIn(jsonNode.get("expires_in").asLong())
                .tokenType(jsonNode.has("token_type") ? jsonNode.get("token_type").asText() : "Bearer")
                .scope(jsonNode.has("scope") ? jsonNode.get("scope").asText() : DEFAULT_SCOPE)
                .issuedAt(LocalDateTime.now())
                .build();
    }

    // ✅ Helper method to parse email from ID token
    private String parseEmailFromIdToken(String idToken) throws Exception {
        // ID token is JWT: header.payload.signature
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid ID token format");
        }

        // Decode payload (second part)
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        JsonNode jsonNode = objectMapper.readTree(payload);

        // Try different email fields
        if (jsonNode.has("email") && !jsonNode.get("email").asText().isEmpty()) {
            return jsonNode.get("email").asText();
        }
        if (jsonNode.has("preferred_username") && !jsonNode.get("preferred_username").asText().isEmpty()) {
            return jsonNode.get("preferred_username").asText();
        }
        if (jsonNode.has("upn") && !jsonNode.get("upn").asText().isEmpty()) {
            return jsonNode.get("upn").asText();
        }

        return null;
    }
}
