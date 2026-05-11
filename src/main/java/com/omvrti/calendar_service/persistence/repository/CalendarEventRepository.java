package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.persistence.entity.CalendarEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEventEntity, String> {

    List<CalendarEventEntity> findByUserEmail(String userEmail);

    List<CalendarEventEntity> findByUserEmailAndProvider(String userEmail, ProviderType provider);

    Optional<CalendarEventEntity> findByExternalIdAndProvider(String externalId, ProviderType provider);

    void deleteByUserEmailAndProvider(String userEmail, ProviderType provider);
}

