package com.omvrti.calendar_service.calendar.sync;

import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.repository.CustomerUserSyncRepository;
import com.omvrti.calendar_service.persistence.service.SyncStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Persists sync status changes in REQUIRES_NEW transactions so they commit
 * immediately regardless of the calling SyncEngine transaction outcome.
 * This prevents the FAILED/SUCCESS status from being rolled back when the
 * outer sync transaction fails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncStatusPersistenceService {

    private final CustomerUserSyncRepository syncRepository;
    private final SyncStatusService syncStatusService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInProgress(Long syncId) {
        syncRepository.findById(syncId).ifPresent(sync -> {
            syncStatusService.findByCode("IN_PROGRESS").ifPresent(sync::setSyncStatus);
            sync.setErrorCode(null);
            sync.setErrorMessage(null);
            syncRepository.save(sync);
            log.debug("Sync {} marked IN_PROGRESS", syncId);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long syncId) {
        syncRepository.findById(syncId).ifPresent(sync -> {
            syncStatusService.findByCode("SUCCESS").ifPresent(sync::setSyncStatus);
            sync.setLastSyncDate(LocalDateTime.now());
            sync.setErrorCode(null);
            sync.setErrorMessage(null);
            syncRepository.save(sync);
            log.debug("Sync {} marked SUCCESS", syncId);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long syncId, String errorMessage) {
        syncRepository.findById(syncId).ifPresent(sync -> {
            syncStatusService.findByCode("FAILED").ifPresent(sync::setSyncStatus);
            sync.setErrorCode("SYNC_ERROR");
            String msg = (errorMessage != null && errorMessage.length() > 200)
                    ? errorMessage.substring(0, 200) : errorMessage;
            sync.setErrorMessage(msg);
            syncRepository.save(sync);
            log.debug("Sync {} marked FAILED: {}", syncId, msg);
        });
    }
}
