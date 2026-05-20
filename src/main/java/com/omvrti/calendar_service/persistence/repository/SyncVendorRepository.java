package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.SyncVendorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SyncVendorRepository extends JpaRepository<SyncVendorEntity, Long> {

    Optional<SyncVendorEntity> findFirstByNameOrderByIdAsc(String name);

    boolean existsByName(String name);

    /** Safe wrapper — uses findFirst to tolerate duplicate master-data rows. */
    default Optional<SyncVendorEntity> findByName(String name) {
        return findFirstByNameOrderByIdAsc(name);
    }

    /** Kept for backward-compatibility with SyncVendorService calls. */
    default Optional<SyncVendorEntity> findByVendorCode(String vendorCode) {
        return findFirstByNameOrderByIdAsc(vendorCode);
    }
}
