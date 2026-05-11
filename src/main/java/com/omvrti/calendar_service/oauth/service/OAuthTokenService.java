package com.omvrti.calendar_service.oauth.service;

import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.OAuthException;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import com.omvrti.calendar_service.persistence.entity.OAuthTokenEntity;
import com.omvrti.calendar_service.persistence.repository.OAuthTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenService {

    private final OAuthTokenRepository tokenRepository;
    private final Map<ProviderType, IOAuthProvider> oauthProviders;

    @Transactional
    public void saveToken(String userEmail, ProviderType provider, OAuthTokenDto tokenDto) {
        log.info("Saving OAuth token for user: {} with provider: {}", userEmail, provider);

        OAuthTokenEntity entity = tokenRepository
            .findByUserEmailAndProvider(userEmail, provider)
            .orElse(new OAuthTokenEntity());

        entity.setUserEmail(userEmail);
        entity.setProvider(provider);
        entity.setAccessToken(tokenDto.getAccessToken());
        entity.setRefreshToken(tokenDto.getRefreshToken());
        entity.setExpiresIn(tokenDto.getExpiresIn());
        entity.setTokenType(tokenDto.getTokenType());
        entity.setScope(tokenDto.getScope());
        entity.setRefreshedAt(LocalDateTime.now());

        tokenRepository.save(entity);
        log.info("Token saved successfully for user: {} with provider: {}", userEmail, provider);
    }

    public OAuthTokenDto getValidToken(String userEmail, ProviderType provider) {
        log.debug("Fetching valid token for user: {} with provider: {}", userEmail, provider);

        OAuthTokenEntity entity = tokenRepository
            .findByUserEmailAndProvider(userEmail, provider)
            .orElseThrow(() -> new OAuthException(
                "NO_TOKEN",
                "No OAuth token found for user: " + userEmail + " with provider: " + provider,
                provider.toString()
            ));

        if (entity.isExpired()) {
            log.info("Token expired for user: {} with provider: {}, refreshing...", userEmail, provider);
            return refreshToken(userEmail, provider);
        }

        return OAuthTokenDto.builder()
            .accessToken(entity.getAccessToken())
            .refreshToken(entity.getRefreshToken())
            .expiresIn(entity.getExpiresIn())
            .tokenType(entity.getTokenType())
            .scope(entity.getScope())
            .issuedAt(entity.getRefreshedAt())
            .build();
    }

    @Transactional
    public OAuthTokenDto refreshToken(String userEmail, ProviderType provider) {
        log.info("Refreshing OAuth token for user: {} with provider: {}", userEmail, provider);

        OAuthTokenEntity entity = tokenRepository
            .findByUserEmailAndProvider(userEmail, provider)
            .orElseThrow(() -> new OAuthException(
                "NO_TOKEN",
                "No refresh token found for user: " + userEmail,
                provider.toString()
            ));

        if (entity.getRefreshToken() == null) {
            throw new OAuthException(
                "NO_REFRESH_TOKEN",
                "No refresh token available for provider: " + provider,
                provider.toString()
            );
        }

        try {
            IOAuthProvider oauthProvider = oauthProviders.get(provider);
            if (oauthProvider == null) {
                throw new OAuthException(
                    "UNSUPPORTED_PROVIDER",
                    "OAuth provider not found: " + provider,
                    provider.toString()
                );
            }

            OAuthTokenDto newTokenDto = oauthProvider.refreshAccessToken(entity.getRefreshToken());
            saveToken(userEmail, provider, newTokenDto);

            log.info("Token refreshed successfully for user: {} with provider: {}", userEmail, provider);
            return newTokenDto;
        } catch (Exception e) {
            log.error("Failed to refresh token for user: {} with provider: {}", userEmail, provider, e);
            throw new OAuthException(
                "REFRESH_FAILED",
                "Failed to refresh OAuth token: " + e.getMessage(),
                provider.toString(),
                e
            );
        }
    }

    @Transactional
    public void revokeToken(String userEmail, ProviderType provider) {
        log.info("Revoking OAuth token for user: {} with provider: {}", userEmail, provider);

        OAuthTokenEntity entity = tokenRepository
            .findByUserEmailAndProvider(userEmail, provider)
            .orElse(null);

        if (entity != null && entity.getAccessToken() != null) {
            try {
                IOAuthProvider oauthProvider = oauthProviders.get(provider);
                if (oauthProvider != null) {
                    oauthProvider.revokeToken(entity.getAccessToken());
                }
            } catch (Exception e) {
                log.warn("Failed to revoke token from provider, but will delete from database", e);
            }

            tokenRepository.deleteByUserEmailAndProvider(userEmail, provider);
            log.info("Token revoked successfully for user: {} with provider: {}", userEmail, provider);
        }
    }

    public String getValidAccessToken(String userEmail, ProviderType provider) {
        return getValidToken(userEmail, provider).getAccessToken();
    }
}

