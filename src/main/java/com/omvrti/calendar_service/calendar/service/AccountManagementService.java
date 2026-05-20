package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.common.dto.ConnectedAccountDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.persistence.entity.CustomerUserEntity;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.repository.CustomerUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountManagementService {

    private final CustomerUserRepository customerUserRepository;
    private final CustomerUserSyncService customerUserSyncService;

    public CustomerUserEntity getOrCreateCustomerUser(String email, String firstName, String lastName) {
        log.debug("Getting or creating customer user: {}", email);
        return customerUserSyncService.getOrCreateCustomerUser(email, firstName, lastName);
    }

    public List<ConnectedAccountDto> getConnectedAccounts(String userEmail) {
        log.debug("Fetching connected accounts for: {}", userEmail);
        CustomerUserEntity customerUser = customerUserRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Customer user not found: " + userEmail));
        return customerUserSyncService.getActiveSyncs(customerUser).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public boolean isProviderConnected(String userEmail, ProviderType provider) {
        return customerUserRepository.findByEmail(userEmail)
                .map(user -> customerUserSyncService.isProviderConnected(user, provider))
                .orElse(false);
    }

    public ConnectedAccountDto getConnectedAccount(String userEmail, ProviderType provider) {
        CustomerUserEntity customerUser = customerUserRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Customer user not found: " + userEmail));
        CustomerUserSyncEntity sync = customerUserSyncService.getSync(customerUser, provider)
                .orElseThrow(() -> new IllegalArgumentException("Provider not connected: " + provider));
        return convertToDto(sync);
    }

    @Transactional
    public void disconnectProvider(String userEmail, ProviderType provider) {
        log.info("Disconnecting {} for user: {}", provider, userEmail);
        CustomerUserEntity customerUser = customerUserRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Customer user not found: " + userEmail));
        customerUserSyncService.disconnectProvider(customerUser, provider);
    }

    public List<ProviderType> getAvailableProviders() {
        return List.of(ProviderType.GOOGLE, ProviderType.OUTLOOK);
    }

    private ConnectedAccountDto convertToDto(CustomerUserSyncEntity sync) {
        ProviderType provider = null;
        if (sync.getSyncVendor() != null) {
            try { provider = ProviderType.valueOf(sync.getSyncVendor().getVendorCode()); }
            catch (IllegalArgumentException ignored) {}
        }
        return ConnectedAccountDto.builder()
                .id(sync.getId())
                .provider(provider)
                .externalUserId(sync.getSyncingAccountReference())
                .isActive(Integer.valueOf(1).equals(sync.getIsActive()))
                .connectedAt(sync.getInsertedOn())
                .lastTokenRefreshAt(sync.getUpdatedOn())
                .scope(sync.getTokenScope())
                .build();
    }
}
