package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.persistence.entity.SyncMetadataEntity;
import com.omvrti.calendar_service.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SyncMetadataRepository extends JpaRepository<SyncMetadataEntity, Long> {
    Optional<SyncMetadataEntity> findByUserAndProvider(UserEntity user, ProviderType provider);
}

