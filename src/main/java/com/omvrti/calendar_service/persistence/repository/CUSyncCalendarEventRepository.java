package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEntity;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CUSyncCalendarEventRepository extends JpaRepository<CUSyncCalendarEventEntity, Long> {

    List<CUSyncCalendarEventEntity> findByCuSyncCalendar(CUSyncCalendarEntity cuSyncCalendar);

    Optional<CUSyncCalendarEventEntity> findByCuSyncCalendarAndCalendarEventReference(
            CUSyncCalendarEntity cuSyncCalendar, String calendarEventReference);

    List<CUSyncCalendarEventEntity> findByCalendarEventReference(String calendarEventReference);

    @Query("SELECT ce FROM CUSyncCalendarEventEntity ce " +
           "WHERE ce.cuSyncCalendar = :calendar " +
           "AND ce.startTimeWithZone >= :startDate " +
           "AND ce.endTimeWithZone <= :endDate " +
           "AND (ce.calendarEventStatus IS NULL OR ce.calendarEventStatus.isCancelled = 0)")
    List<CUSyncCalendarEventEntity> findByCuSyncCalendarAndEventStartDateBetweenAndIsCancelledFalse(
            @Param("calendar") CUSyncCalendarEntity cuSyncCalendar,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    @Query("SELECT ce FROM CUSyncCalendarEventEntity ce " +
           "WHERE ce.cuSyncCalendar = :calendar " +
           "AND ce.endTimeWithZone > CURRENT_TIMESTAMP " +
           "AND (ce.calendarEventStatus IS NULL OR ce.calendarEventStatus.isCancelled = 0)")
    List<CUSyncCalendarEventEntity> findUpcomingEvents(@Param("calendar") CUSyncCalendarEntity cuSyncCalendar);

    @Query("SELECT COUNT(ce) FROM CUSyncCalendarEventEntity ce WHERE ce.cuSyncCalendar = :calendar")
    long countEventsByCalendar(@Param("calendar") CUSyncCalendarEntity cuSyncCalendar);
}
