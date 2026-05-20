package com.omvrti.calendar_service.oauth.service;

import com.omvrti.calendar_service.calendar.service.CustomerUserSyncService;
import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.OAuthException;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Delegates to TokenRefreshService / CustomerUserSyncService using the new entity model.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenService {

    private final TokenRefreshService tokenRefreshService;
    private final CustomerUserSyncService customerUserSyncService;
    private final Map<ProviderType, IOAuthProvider> oauthProviders;

    @Transactional
    public void saveToken(String userEmail, ProviderType provider, OAuthTokenDto tokenDto) {
        log.info("Saving OAuth token for user: {} with provider: {}", userEmail, provider);
        customerUserSyncService.getSyncByEmail(userEmail, provider).ifPresent(sync ->
                tokenRefreshService.saveCustomerUserSyncTokens(sync, tokenDto));
    }

    public OAuthTokenDto getValidToken(String userEmail, ProviderType provider) {
        log.debug("Fetching valid token for user: {} with provider: {}", userEmail, provider);
        CustomerUserSyncEntity sync = customerUserSyncService.getSyncByEmail(userEmail, provider)
                .orElseThrow(() -> new OAuthException("NO_TOKEN",
                        "No OAuth token found for user: " + userEmail + " with provider: " + provider,
                        provider.toString()));

        String accessToken = tokenRefreshService.getValidAccessToken(sync, provider);
        return OAuthTokenDto.builder().accessToken(accessToken).build();
    }

    public String getValidAccessToken(String userEmail, ProviderType provider) {
        return getValidToken(userEmail, provider).getAccessToken();
    }

    @Transactional
    public OAuthTokenDto refreshToken(String userEmail, ProviderType provider) {
        log.info("Refreshing OAuth token for user: {} with provider: {}", userEmail, provider);
        CustomerUserSyncEntity sync = customerUserSyncService.getSyncByEmail(userEmail, provider)
                .orElseThrow(() -> new OAuthException("NO_TOKEN",
                        "No token found for user: " + userEmail, provider.toString()));
        String accessToken = tokenRefreshService.getValidAccessToken(sync, provider);
        return OAuthTokenDto.builder().accessToken(accessToken).build();
    }

    @Transactional
    public void revokeToken(String userEmail, ProviderType provider) {
        log.info("Revoking OAuth token for user: {} with provider: {}", userEmail, provider);
        customerUserSyncService.getSyncByEmail(userEmail, provider).ifPresent(sync -> {
            try {
                IOAuthProvider oauthProvider = oauthProviders.get(provider);
                if (oauthProvider != null && sync.getAccessToken() != null) {
                    oauthProvider.revokeToken(sync.getAccessToken());
                }
            } catch (Exception e) {
                log.warn("Failed to revoke token from provider: {}", e.getMessage());
            }
        });
    }
}
