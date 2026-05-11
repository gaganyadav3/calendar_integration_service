package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.common.dto.ConnectedAccountDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import com.omvrti.calendar_service.persistence.entity.UserEntity;
import com.omvrti.calendar_service.persistence.repository.ConnectedAccountRepository;
import com.omvrti.calendar_service.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing connected calendar accounts
 * Handles connecting, disconnecting, and listing accounts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountManagementService {

    private final ConnectedAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TokenRefreshService tokenRefreshService;

    /**
     * Get or create user
     */
    public UserEntity getOrCreateUser(String email, String firstName, String lastName) {
        log.debug("Getting or creating user: {}", email);

        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    UserEntity user = UserEntity.builder()
                            .email(email)
                            .firstName(firstName)
                            .lastName(lastName)
                            .build();
                    return userRepository.save(user);
                });
    }

    /**
     * Get all connected accounts for a user
     */
    public List<ConnectedAccountDto> getConnectedAccounts(String userEmail) {
        log.debug("Fetching connected accounts for: {}", userEmail);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return accountRepository.findByUser(user).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Check if provider is connected
     */
    public boolean isProviderConnected(String userEmail, ProviderType provider) {
        log.debug("Checking if {} connected for {}", provider, userEmail);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return accountRepository.existsByUserAndProvider(user, provider) &&
               accountRepository.findByUserAndProvider(user, provider)
                       .map(ConnectedAccountEntity::isActive)
                       .orElse(false);
    }

    /**
     * Get connected account details
     */
    public ConnectedAccountDto getConnectedAccount(String userEmail, ProviderType provider) {
        log.debug("Fetching connected account for {} - {}", userEmail, provider);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        ConnectedAccountEntity account = accountRepository.findByUserAndProvider(user, provider)
                .orElseThrow(() -> new IllegalArgumentException("Provider not connected: " + provider));

        return convertToDto(account);
    }

    /**
     * Disconnect provider account
     */
    @Transactional
    public void disconnectProvider(String userEmail, ProviderType provider) {
        log.info("Disconnecting {} for user: {}", provider, userEmail);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        tokenRefreshService.disconnectAccount(user, provider);
    }

    /**
     * Get list of available providers
     */
    public List<ProviderType> getAvailableProviders() {
        return List.of(ProviderType.GOOGLE, ProviderType.OUTLOOK);
    }

    private ConnectedAccountDto convertToDto(ConnectedAccountEntity entity) {
        return ConnectedAccountDto.builder()
                .id(entity.getId())
                .provider(entity.getProvider())
                .externalUserId(entity.getExternalUserId())
                .isActive(entity.isActive())
                .connectedAt(entity.getConnectedAt())
                .lastTokenRefreshAt(entity.getLastTokenRefreshAt())
                .scope(entity.getScope())
                .build();
    }
}

