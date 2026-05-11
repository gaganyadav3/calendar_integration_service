package com.omvrti.calendar_service.oauth.service;

import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.ProviderAuthException;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import com.omvrti.calendar_service.persistence.entity.UserEntity;
import com.omvrti.calendar_service.persistence.repository.ConnectedAccountRepository;
import com.omvrti.calendar_service.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRefreshService {

    private final ConnectedAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final Map<ProviderType, IOAuthProvider> oauthProviders;

    private static final int REFRESH_THRESHOLD_MINUTES = 5;

    /**
     * Get valid access token, refreshing if necessary
     */
    @Transactional
    @Retryable(
        retryFor = {ProviderAuthException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String getValidAccessToken(String userEmail, ProviderType provider) {
        log.debug("Getting valid access token for {} - {}", userEmail, provider);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ProviderAuthException(provider.name(), "User not found: " + userEmail));

        ConnectedAccountEntity account = accountRepository.findByUserAndProvider(user, provider)
                .orElseThrow(() -> new ProviderAuthException(
                    provider.name(),
                    String.format("No connected account for %s", provider)
                ));

        if (!account.isActive()) {
            throw new ProviderAuthException(provider.name(), "Account is not active");
        }

        if (account.isTokenExpired()) {
            log.info("Token expired, refreshing for {} - {}", userEmail, provider);
            refreshAccessToken(user, provider);
            // Reload account after refresh
            account = accountRepository.findByUserAndProvider(user, provider).orElseThrow();
        }

        return account.getAccessToken();
    }

    /**
     * Refresh access token using refresh token
     */
    @Transactional
    public void refreshAccessToken(UserEntity user, ProviderType provider) {
        log.info("Refreshing access token for {} - {}", user.getEmail(), provider);

        ConnectedAccountEntity account = accountRepository.findByUserAndProvider(user, provider)
                .orElseThrow(() -> new ProviderAuthException(
                    provider.name(),
                    "No connected account found"
                ));

        if (account.getRefreshToken() == null || account.getRefreshToken().isEmpty()) {
            throw new ProviderAuthException(
                provider.name(),
                "No refresh token available. Please reconnect your account."
            );
        }

        try {
            IOAuthProvider oauthProvider = oauthProviders.get(provider);
            if (oauthProvider == null) {
                throw new ProviderAuthException(provider.name(), "Provider not implemented");
            }

            OAuthTokenDto newToken = oauthProvider.refreshAccessToken(account.getRefreshToken());

            // Update account with new token
            account.setAccessToken(newToken.getAccessToken());
            if (newToken.getRefreshToken() != null) {
                account.setRefreshToken(newToken.getRefreshToken());
            }
            if (newToken.getIdToken() != null) {
                account.setIdToken(newToken.getIdToken());
            }
            account.setLastTokenRefreshAt(LocalDateTime.now());
            if (newToken.getExpiresIn() != null) {
                account.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(newToken.getExpiresIn()));
            }

            accountRepository.save(account);
            log.info("Token refreshed successfully for {} - {}", user.getEmail(), provider);
        } catch (Exception e) {
            log.error("Failed to refresh token", e);
            throw new ProviderAuthException(
                provider.name(),
                "Failed to refresh token: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Save new OAuth tokens after authentication
     */
    @Transactional
    public ConnectedAccountEntity saveOAuthTokens(UserEntity user, ProviderType provider,
                                                  OAuthTokenDto tokenDto, String externalUserId) {
        log.info("Saving OAuth tokens for {} - {}", user.getEmail(), provider);

        ConnectedAccountEntity account = accountRepository.findByUserAndProvider(user, provider)
                .orElse(ConnectedAccountEntity.builder()
                    .user(user)
                    .provider(provider)
                    .build());

        account.setAccessToken(tokenDto.getAccessToken());
        account.setRefreshToken(tokenDto.getRefreshToken());
        account.setIdToken(tokenDto.getIdToken());
        account.setScope(tokenDto.getScope());
        account.setExternalUserId(externalUserId);
        account.setActive(true);
        account.setLastTokenRefreshAt(LocalDateTime.now());

        if (tokenDto.getExpiresIn() != null) {
            account.setAccessTokenExpiresAt(
                LocalDateTime.now().plusSeconds(tokenDto.getExpiresIn())
            );
        }

        ConnectedAccountEntity saved = accountRepository.save(account);
        log.info("OAuth tokens saved for {} - {}", user.getEmail(), provider);
        return saved;
    }

    /**
     * Revoke and disconnect account
     */
    @Transactional
    public void disconnectAccount(UserEntity user, ProviderType provider) {
        log.info("Disconnecting {} for user: {}", provider, user.getEmail());

        ConnectedAccountEntity account = accountRepository.findByUserAndProvider(user, provider)
                .orElseThrow(() -> new ProviderAuthException(provider.name(), "Account not connected"));

        try {
            IOAuthProvider oauthProvider = oauthProviders.get(provider);
            if (oauthProvider != null && account.getAccessToken() != null) {
                oauthProvider.revokeToken(account.getAccessToken());
            }
        } catch (Exception e) {
            log.warn("Failed to revoke token from provider, but will disconnect", e);
        }

        account.setActive(false);
        account.setAccessToken(null);
        account.setRefreshToken(null);
        account.setIdToken(null);

        accountRepository.save(account);
        log.info("Account disconnected: {} - {}", user.getEmail(), provider);
    }
}
