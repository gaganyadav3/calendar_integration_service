package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.SyncStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SyncStatusRepository extends JpaRepository<SyncStatusEntity, Long> {

    Optional<SyncStatusEntity> findFirstByNameOrderByIdAsc(String name);

    /** Safe wrapper — uses findFirst to tolerate duplicate master-data rows. */
    default Optional<SyncStatusEntity> findByName(String name) {
        return findFirstByNameOrderByIdAsc(name);
    }

    /** Kept for backward-compatibility with SyncStatusService calls. */
    default Optional<SyncStatusEntity> findByStatusCode(String statusCode) {
        return findFirstByNameOrderByIdAsc(statusCode);
    }
}
