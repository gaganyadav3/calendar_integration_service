package com.omvrti.calendar_service.persistence.service;

import com.omvrti.calendar_service.persistence.entity.SyncStatusEntity;
import com.omvrti.calendar_service.persistence.repository.SyncStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for resolving SyncStatus lookup records.
 */
@Service
@RequiredArgsConstructor
public class SyncStatusService {

    private final SyncStatusRepository syncStatusRepository;

    public Optional<SyncStatusEntity> findByCode(String statusCode) {
        return syncStatusRepository.findByStatusCode(statusCode);
    }
}
