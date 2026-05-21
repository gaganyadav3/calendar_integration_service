package com.omvrti.calendar_service.calendar.sync;

import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.util.EventEntityMapper;
import com.omvrti.calendar_service.persistence.entity.*;
import com.omvrti.calendar_service.persistence.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merges provider events into the DB inside REQUIRES_NEW transactions so that:
 * 1. A failure on one event does not corrupt or roll back the calling SyncEngine transaction.
 * 2. Attendees and reminders are saved atomically with the event in the same sub-transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventMergePersistenceService {

    private final CUSyncCalendarEventRepository eventRepository;
    private final CalendarEventStatusRepository calendarEventStatusRepository;
    private final EventEntityMapper eventMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Merges one provider event (and its attendees/reminders) into the DB.
     * Each call runs in its own REQUIRES_NEW transaction.
     *
     * @param calendarId DB id of the owning CUSyncCalendarEntity
     * @param dto        provider event data
     * @return true if the event was saved/updated, false if it was cancelled or skipped
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean mergeEvent(Long calendarId, EventDto dto) {
        if (dto.getExternalId() == null || dto.getExternalId().isBlank()) {
            log.warn("Skipping event with null/blank externalId for calendar {}", calendarId);
            return false;
        }

        // Use a lightweight proxy — avoids loading the calendar in this sub-transaction
        CUSyncCalendarEntity calendarRef =
                entityManager.getReference(CUSyncCalendarEntity.class, calendarId);

        CUSyncCalendarEventEntity existing = eventRepository
                .findByCuSyncCalendarAndCalendarEventReference(calendarRef, dto.getExternalId())
                .orElse(null);

        if (dto.isCancelled()) {
            if (existing != null) markCancelled(existing);
            return false;
        }

        CUSyncCalendarEventEntity event = (existing != null) ? existing
                : CUSyncCalendarEventEntity.builder()
                        .cuSyncCalendar(calendarRef)
                        .calendarEventReference(dto.getExternalId())
                        .isAllDay(0)
                        .isVisible(1)
                        .build();

        eventMapper.updateEntityFromDto(dto, event);
        CUSyncCalendarEventEntity saved = eventRepository.save(event);

        // Guests and reminders within the same REQUIRES_NEW transaction (atomic with event)
        if (dto.getAttendees() != null) eventMapper.syncGuests(saved, dto.getAttendees());
        if (dto.getReminders() != null) eventMapper.syncReminders(saved, dto.getReminders());

        return true;
    }

    private void markCancelled(CUSyncCalendarEventEntity event) {
        calendarEventStatusRepository.findFirstByIsCancelledOrderByIdAsc(1)
                .ifPresentOrElse(
                        event::setCalendarEventStatus,
                        () -> log.warn("No CANCELLED CalendarEventStatus found in DB — run master data init"));
        eventRepository.save(event);
        log.debug("Marked event {} as cancelled", event.getCalendarEventReference());
    }
}
