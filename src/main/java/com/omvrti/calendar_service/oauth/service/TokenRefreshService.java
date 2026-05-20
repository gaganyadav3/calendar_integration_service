package com.omvrti.calendar_service.oauth.service;

import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.OAuthException;
import com.omvrti.calendar_service.common.exception.ProviderAuthException;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.repository.CustomerUserSyncRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRefreshService {

    private final CustomerUserSyncRepository customerUserSyncRepository;
    private final Map<ProviderType, IOAuthProvider> oauthProviders;

    /**
     * Return a valid access token for a CustomerUserSyncEntity, refreshing if the token is expired.
     */
    @Transactional
    @Retryable(retryFor = {ProviderAuthException.class}, maxAttempts = 2,
               backoff = @Backoff(delay = 1000))
    public String getValidAccessToken(CustomerUserSyncEntity sync, ProviderType provider) {
        if (sync.isTokenExpired()) {
            log.info("Token expired for {} - {}, refreshing", sync.getSyncEmail(), provider);
            sync = refreshCustomerUserSyncToken(sync, provider);
        }
        return sync.getAccessToken();
    }

    /**
     * Refresh access token.
     * Since REFRESH_TOKEN is not stored in the new schema the only option is re-authentication.
     */
    @Transactional
    public CustomerUserSyncEntity refreshCustomerUserSyncToken(CustomerUserSyncEntity sync, ProviderType provider) {
        log.info("Refreshing token for {} - {}", sync.getSyncEmail(), provider);

        String refreshToken = sync.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ProviderAuthException(provider.name(),
                    "No refresh token available for " + sync.getSyncEmail() + ". Please re-authenticate.");
        }

        IOAuthProvider oauthProvider = oauthProviders.get(provider);
        if (oauthProvider == null) {
            throw new ProviderAuthException(provider.name(), "OAuth provider not registered: " + provider);
        }

        try {
            OAuthTokenDto newToken = oauthProvider.refreshAccessToken(refreshToken);
            sync.setAccessToken(newToken.getAccessToken());
            if (newToken.getRefreshToken() != null) sync.setRefreshToken(newToken.getRefreshToken());
            if (newToken.getIdToken() != null) sync.setIdToken(newToken.getIdToken());
            if (newToken.getScope() != null) sync.setTokenScope(newToken.getScope());
            if (newToken.getExpiresIn() != null) {
                sync.setAccessTokenExpiryDate(OffsetDateTime.now().plusSeconds(newToken.getExpiresIn()));
            }
            CustomerUserSyncEntity saved = customerUserSyncRepository.save(sync);
            log.info("Token refreshed for {} - {}", sync.getSyncEmail(), provider);
            return saved;
        } catch (OAuthException e) {
            if ("invalid_grant".equalsIgnoreCase(e.getErrorCode())
                    || (e.getMessage() != null && e.getMessage().contains("invalid_grant"))) {
                log.error("invalid_grant for {} - {}: re-auth required", sync.getSyncEmail(), provider);
                customerUserSyncRepository.save(sync);
                throw new ProviderAuthException(provider.name(),
                        "Refresh token revoked or expired. Re-authenticate required.");
            }
            throw new ProviderAuthException(provider.name(), "Token refresh failed: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void saveCustomerUserSyncTokens(CustomerUserSyncEntity sync, OAuthTokenDto tokenDto) {
        sync.setAccessToken(tokenDto.getAccessToken());
        if (tokenDto.getRefreshToken() != null) sync.setRefreshToken(tokenDto.getRefreshToken());
        if (tokenDto.getIdToken() != null) sync.setIdToken(tokenDto.getIdToken());
        if (tokenDto.getScope() != null) sync.setTokenScope(tokenDto.getScope());
        if (tokenDto.getExpiresIn() != null) {
            sync.setAccessTokenExpiryDate(OffsetDateTime.now().plusSeconds(tokenDto.getExpiresIn()));
        }
        customerUserSyncRepository.save(sync);
    }
}
