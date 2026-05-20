package com.omvrti.calendar_service.calendar.sync;

import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.SyncException;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.repository.CustomerUserSyncRepository;
import com.omvrti.calendar_service.persistence.service.SyncVendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scheduled synchronisation service.
 * Iterates all active CustomerUserSync records and triggers incremental sync every 5 minutes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledSyncService {

    private final SyncEngine syncEngine;
    private final CustomerUserSyncRepository customerUserSyncRepository;
    private final SyncVendorService syncVendorService;

    /**
     * Sync all active accounts every 5 minutes.
     */
    @Scheduled(cron = "0 0,5,10,15,20,25,30,35,40,45,50,55 * * * *")
    public void syncAllAccounts() {
        log.debug("Scheduled sync started");

        List<CustomerUserSyncEntity> activeSyncs =
                customerUserSyncRepository.findAllActiveSyncsWithCustomerUser();

        log.info("Found {} active syncs to process", activeSyncs.size());

        for (CustomerUserSyncEntity sync : activeSyncs) {
            try {
                syncAccount(sync);
            } catch (Exception e) {
                log.error("Error syncing {} - {}: {}",
                        sync.getCustomerUser().getEmail(),
                        sync.getSyncVendor().getVendorCode(),
                        e.getMessage());
            }
        }

        log.debug("Scheduled sync completed");
    }

    /**
     * Sync a single CustomerUserSync.
     */
    public void syncAccount(CustomerUserSyncEntity sync) {
        String userEmail = sync.getCustomerUser().getEmail();
        String vendorCode = sync.getSyncVendor().getVendorCode();
        log.debug("Syncing account {} - {}", userEmail, vendorCode);

        try {
            ProviderType provider = ProviderType.valueOf(vendorCode);
            SyncEngine.SyncResult result = syncEngine.sync(userEmail, provider);
            log.info("Sync result: {}", result);
        } catch (SyncException e) {
            log.error("Sync failed for {} - {}: {}", userEmail, vendorCode, e.getMessage());
            throw e;
        }
    }

    /**
     * Force sync for a specific user and provider.
     */
    public SyncEngine.SyncResult forceSyncAccount(String userEmail, ProviderType provider) {
        log.info("Force sync triggered for {} - {}", userEmail, provider);
        return syncEngine.sync(userEmail, provider);
    }
}
