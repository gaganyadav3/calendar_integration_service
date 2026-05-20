package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEventEntity;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEventGuestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CUSyncCalendarEventGuestRepository extends JpaRepository<CUSyncCalendarEventGuestEntity, Long> {

    List<CUSyncCalendarEventGuestEntity> findByCuSyncCalendarEvent(CUSyncCalendarEventEntity event);

    @Query("SELECT g FROM CUSyncCalendarEventGuestEntity g " +
           "WHERE g.cuSyncCalendarEvent = :event AND g.isOrganizer = 1")
    List<CUSyncCalendarEventGuestEntity> findOrganizersByEvent(@Param("event") CUSyncCalendarEventEntity event);

    /** Backward-compat */
    default List<CUSyncCalendarEventGuestEntity> findByCuSyncCalendarEventAndIsOrganiserTrue(
            CUSyncCalendarEventEntity event) {
        return findOrganizersByEvent(event);
    }
}
