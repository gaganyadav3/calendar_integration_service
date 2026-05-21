package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.CalendarEventStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CalendarEventStatusRepository extends JpaRepository<CalendarEventStatusEntity, Long> {

    Optional<CalendarEventStatusEntity> findFirstByNameOrderByIdAsc(String name);

    /** Safe wrapper — uses findFirst to tolerate duplicate master-data rows. */
    default Optional<CalendarEventStatusEntity> findByName(String name) {
        return findFirstByNameOrderByIdAsc(name);
    }

    /** Backward-compat — old column was EVENT_STATUS_CODE, now NAME. */
    default Optional<CalendarEventStatusEntity> findByEventStatusCode(String eventStatusCode) {
        return findFirstByNameOrderByIdAsc(eventStatusCode);
    }

    /** Direct query for the CANCELLED status — avoids loading the full table in markCancelled(). */
    Optional<CalendarEventStatusEntity> findFirstByIsCancelledOrderByIdAsc(Integer isCancelled);
}
