package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.persistence.entity.CustomerUserEntity;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.entity.SyncStatusEntity;
import com.omvrti.calendar_service.persistence.entity.SyncVendorEntity;
import com.omvrti.calendar_service.persistence.repository.CustomerUserRepository;
import com.omvrti.calendar_service.persistence.repository.CustomerUserSyncRepository;
import com.omvrti.calendar_service.persistence.repository.SyncStatusRepository;
import com.omvrti.calendar_service.persistence.service.SyncVendorService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerUserSyncService {

    private final CustomerUserRepository customerUserRepository;
    private final CustomerUserSyncRepository customerUserSyncRepository;
    private final SyncVendorService syncVendorService;
    private final SyncStatusRepository syncStatusRepository;

    // ── User management ───────────────────────────────────────────────────────

//    @Transactional
//    public CustomerUserEntity getOrCreateCustomerUser(String email, String firstName, String lastName) {
//        log.debug("Getting or creating customer user: {}", email);
//        Optional<CustomerUserEntity> existing = customerUserRepository.findByEmail(email);
//        if (existing.isPresent()) {
//            return existing.get();
//        }
//        try {
//            // Insert with customerId=0 to satisfy the Oracle NOT NULL constraint,
//            // then update it to match the generated ID (customer == user in this service).
//            CustomerUserEntity created = customerUserRepository.saveAndFlush(
//                    CustomerUserEntity.builder()
//                            .email(email)
//                            .firstName(firstName != null ? firstName : "")
//                            .customerId(0L)
//                            .build());
//            created.setCustomerId(created.getId());
//            created = customerUserRepository.saveAndFlush(created);
//            log.debug("Created new customer user: id={} email={}", created.getId(), email);
//            return created;
//        } catch (DataIntegrityViolationException e) {
//            // Concurrent request already created the user — fetch and return the existing row
//            return customerUserRepository.findByEmail(email)
//                    .orElseThrow(() -> new IllegalStateException("Failed to create/find user: " + email));
//        }
//    }
@Transactional(readOnly = true)
public CustomerUserEntity getCustomerUser(String email) {
    log.debug("Fetching customer user: {}", email);

    return customerUserRepository.findByEmail(email)
            .orElseThrow(() -> new EntityNotFoundException(
                    "Customer user not found with email: " + email));
}

    // ── Sync management ───────────────────────────────────────────────────────

    @Transactional
    public CustomerUserSyncEntity createOrUpdateSync(
            CustomerUserEntity customerUser, ProviderType provider,
            String syncEmail, String displayName,
            String accessToken, String refreshToken, String idToken) {

        log.debug("Creating/updating sync for {} - {}", customerUser.getEmail(), provider);
        SyncVendorEntity syncVendor = syncVendorService.getOrCreateVendor(provider);

        CustomerUserSyncEntity sync = customerUserSyncRepository
                .findByCustomerUserAndSyncVendor(customerUser, syncVendor)
                .orElseGet(() -> CustomerUserSyncEntity.builder()
                        .customerUser(customerUser)
                        .syncVendor(syncVendor)
                        .syncStatus(requireStatus("PENDING", "SUCCESS"))
                        .build());

        if (syncEmail != null) sync.setSyncEmail(syncEmail);
        if (sync.getSyncEmail() == null) sync.setSyncEmail(customerUser.getEmail());
        if (displayName != null) sync.setDisplayName(displayName);
        if (accessToken != null) sync.setAccessToken(accessToken);
        if (refreshToken != null) sync.setRefreshToken(refreshToken);
        if (idToken != null) sync.setIdToken(idToken);

        // Promote to SUCCESS whenever real tokens are present
        if (accessToken != null) {
            sync.setSyncStatus(requireStatus("SUCCESS", "PENDING"));
        }

        return customerUserSyncRepository.save(sync);
    }

    @Transactional
    public CustomerUserSyncEntity saveTokens(
            CustomerUserEntity customerUser, ProviderType provider,
            String syncEmail, String displayName, OAuthTokenDto tokenDto) {

        log.info("DIAG saveTokens: email={}, provider={}, customerUser.id={}",
                customerUser.getEmail(), provider, customerUser.getId());
        if (customerUser.getId() == null) {
            throw new IllegalStateException(
                    "CustomerUserEntity has null id for email=" + customerUser.getEmail()
                    + ". User must exist in CUSTOMER_USER table before OAuth sync.");
        }
        SyncVendorEntity syncVendor = syncVendorService.getOrCreateVendor(provider);

        // When building a new sync, start with a valid status — SUCCESS is set immediately below
        CustomerUserSyncEntity sync = customerUserSyncRepository
                .findByCustomerUserAndSyncVendor(customerUser, syncVendor)
                .orElseGet(() -> CustomerUserSyncEntity.builder()
                        .customerUser(customerUser)
                        .syncVendor(syncVendor)
                        .syncStatus(requireStatus("SUCCESS", "PENDING"))
                        .build());

        if (syncEmail != null) sync.setSyncEmail(syncEmail);
        if (sync.getSyncEmail() == null) sync.setSyncEmail(customerUser.getEmail());
        if (displayName != null) sync.setDisplayName(displayName);

        sync.setAccessToken(tokenDto.getAccessToken());
        if (tokenDto.getRefreshToken() != null) sync.setRefreshToken(tokenDto.getRefreshToken());
        if (tokenDto.getIdToken() != null) sync.setIdToken(tokenDto.getIdToken());
        if (tokenDto.getScope() != null) sync.setTokenScope(tokenDto.getScope());
        if (tokenDto.getExpiresIn() != null) {
            sync.setAccessTokenExpiryDate(OffsetDateTime.now().plusSeconds(tokenDto.getExpiresIn()));
        }

        // Mark provider as active/connected after successful token receipt
        sync.setSyncStatus(requireStatus("SUCCESS", "PENDING"));

        CustomerUserSyncEntity saved = customerUserSyncRepository.save(sync);
        log.info("Tokens saved for {} - {} (statusId={})", customerUser.getEmail(), provider, saved.getSyncStatus().getId());
        return saved;
    }

    // ── Lookups ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<CustomerUserSyncEntity> getSync(CustomerUserEntity customerUser, ProviderType provider) {
        SyncVendorEntity syncVendor = syncVendorService.getVendor(provider);
        log.debug("Sync lookup: user.id={}, email={}, vendor={} (id={})",
                customerUser.getId(), customerUser.getEmail(), provider, syncVendor.getId());
        Optional<CustomerUserSyncEntity> result =
                customerUserSyncRepository.findByCustomerUserIdAndSyncVendorId(
                        customerUser.getId(), syncVendor.getId());
        log.info("Sync lookup for {} - {}: {}", customerUser.getEmail(), provider,
                result.map(s -> "FOUND id=" + s.getId()
                        + " status=" + (s.getSyncStatus() != null ? s.getSyncStatus().getName() : "null")
                        + " isActive=" + s.getIsActive()).orElse("NOT FOUND"));
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<CustomerUserSyncEntity> getSyncByEmail(String email, ProviderType provider) {
        return customerUserRepository.findByEmail(email).flatMap(u -> getSync(u, provider));
    }

    public boolean isProviderConnected(CustomerUserEntity customerUser, ProviderType provider) {
        return getSync(customerUser, provider)
                .map(s -> Integer.valueOf(1).equals(s.getIsActive()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<CustomerUserSyncEntity> getActiveSyncs(CustomerUserEntity customerUser) {
        return customerUserSyncRepository.findByCustomerUserAndIsActiveTrue(customerUser);
    }

    // ── Token management ──────────────────────────────────────────────────────

    @Transactional
    public void updateTokens(CustomerUserSyncEntity sync,
                             String accessToken, String refreshToken,
                             String idToken, Long expiresInSeconds) {
        sync.setAccessToken(accessToken);
        if (refreshToken != null) sync.setRefreshToken(refreshToken);
        if (idToken != null) sync.setIdToken(idToken);
        if (expiresInSeconds != null) {
            sync.setAccessTokenExpiryDate(OffsetDateTime.now().plusSeconds(expiresInSeconds));
        }
        customerUserSyncRepository.save(sync);
    }

    // ── Disconnect ────────────────────────────────────────────────────────────

    @Transactional
    public void disconnectProvider(CustomerUserEntity customerUser, ProviderType provider) {
        log.info("Disconnecting {} for user: {}", provider, customerUser.getEmail());
        getSync(customerUser, provider).ifPresent(sync -> {
            sync.setSyncStatus(requireStatus("FAILED", "PENDING"));
            customerUserSyncRepository.save(sync);
        });
    }

    // ── Status helper ─────────────────────────────────────────────────────────

    /**
     * Looks up the primary status by name, falls back to the secondary, then to any row in the table.
     * Prevents IllegalStateException from crashing the entire OAuth/sync flow when master data
     * rows are missing (e.g. due to silent INSERT failures from unmapped Oracle NOT NULL columns).
     */
    private SyncStatusEntity requireStatus(String primary, String fallback) {
        return syncStatusRepository.findByName(primary)
                .or(() -> syncStatusRepository.findByName(fallback))
                .or(() -> syncStatusRepository.findAll().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "SYNC_STATUS table is completely empty — master data initialization failed. " +
                        "Check startup logs for 'Could not insert sync status' warnings."));
    }
}
