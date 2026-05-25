package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.SyncVendorEntity;
import com.omvrti.calendar_service.persistence.entity.SyncVendorLangEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SyncVendorLangRepository extends JpaRepository<SyncVendorLangEntity, Long> {

    Optional<SyncVendorLangEntity> findFirstBySyncVendorAndLanguageIdOrderByIdAsc(
            SyncVendorEntity syncVendor, Integer languageId);

    List<SyncVendorLangEntity> findBySyncVendor(SyncVendorEntity syncVendor);

    @Query("SELECT l FROM SyncVendorLangEntity l WHERE l.syncVendor = :vendor AND l.languageId = :langId")
    Optional<SyncVendorLangEntity> findByVendorAndLanguage(
            @Param("vendor") SyncVendorEntity vendor,
            @Param("langId") Integer langId);
}
