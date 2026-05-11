package com.omvrti.calendar_service.calendar.sync;

import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.SyncException;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import com.omvrti.calendar_service.persistence.repository.ConnectedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scheduled synchronization service
 * Runs periodic sync for all active connected accounts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledSyncService {

    private final SyncEngine syncEngine;
    private final ConnectedAccountRepository accountRepository;

    /**
     * Sync all active accounts every 5 minutes
     * Run at a specific time to spread load (cron: minute 0 and 30 of every hour)
     */
    @Scheduled(cron = "0 0,5,10,15,20,25,30,35,40,45,50,55 * * * *")
    public void syncAllAccounts() {
        log.debug("Starting scheduled sync for all active accounts");

        try {
            List<ConnectedAccountEntity> activeAccounts = accountRepository.findByIsActiveTrueWithUser();

            log.info("Found {} active accounts to sync", activeAccounts.size());

            for (ConnectedAccountEntity account : activeAccounts) {
                try {
                    syncAccount(account);
                } catch (Exception e) {
                    log.error("Error syncing account {} - {}", account.getUser().getEmail(), account.getProvider(), e);
                    // Continue with next account
                }
            }

            log.debug("Scheduled sync completed");

        } catch (Exception e) {
            log.error("Error in scheduled sync", e);
        }
    }

    /**
     * Sync a single account
     */
    public void syncAccount(ConnectedAccountEntity account) {
        log.debug("Syncing account {} - {}", account.getUser().getEmail(), account.getProvider());

        try {
            SyncEngine.SyncResult result = syncEngine.sync(account.getUser().getEmail(), account.getProvider());
            log.info("Sync result: {}", result);
        } catch (SyncException e) {
            log.error("Sync failed for {} - {}: {}",
                account.getUser().getEmail(), account.getProvider(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during sync", e);
            throw new SyncException("SYNC_ERROR", "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Force sync for a specific user and provider
     */
    public SyncEngine.SyncResult forceSyncAccount(String userEmail, ProviderType provider) {
        log.info("Force syncing {} - {}", userEmail, provider);
        return syncEngine.sync(userEmail, provider);
    }
}

