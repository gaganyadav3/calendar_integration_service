package com.omvrti.calendar_service.oauth.provider;

import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;

/**
 * Interface for OAuth providers.
 * Implement this for each OAuth service (Google, Microsoft, etc.)
 */
public interface IOAuthProvider {

    /**
     * Get the provider type
     */
    ProviderType getProviderType();

    /**
     * Get the authorization URL for user login
     */
    String getAuthorizationUrl(String state, String redirectUri, String[] scopes);

    /**
     * Exchange authorization code for access token
     */
    OAuthTokenDto exchangeCodeForToken(String code, String redirectUri);

    /**
     * Refresh the access token using refresh token
     */
    OAuthTokenDto refreshAccessToken(String refreshToken);

    /**
     * Revoke the access token and refresh token
     */
    void revokeToken(String token);

    /**
     * Get user profile information
     */
    String getUserEmail(OAuthTokenDto tokenDto);
}
