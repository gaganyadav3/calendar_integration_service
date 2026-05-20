package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEventEntity;
import com.omvrti.calendar_service.persistence.entity.EventReminderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventReminderRepository extends JpaRepository<EventReminderEntity, Long> {

    List<EventReminderEntity> findByCuSyncCalendarEvent(CUSyncCalendarEventEntity event);

    void deleteByCuSyncCalendarEvent(CUSyncCalendarEventEntity event);
}

