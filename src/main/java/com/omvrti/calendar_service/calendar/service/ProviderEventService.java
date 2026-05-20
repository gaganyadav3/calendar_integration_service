package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.util.EventEntityMapper;
import com.omvrti.calendar_service.persistence.entity.*;
import com.omvrti.calendar_service.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderEventService {

    private final CUSyncCalendarEventRepository eventRepository;
    private final CUSyncCalendarEventGuestRepository guestRepository;
    private final EventReminderRepository reminderRepository;
    private final CalendarEventStatusRepository calendarEventStatusRepository;
    private final EventEntityMapper eventMapper;

    @Transactional
    public CUSyncCalendarEventEntity createOrUpdateEvent(CUSyncCalendarEntity calendar, EventDto eventDto) {
        log.debug("Creating/updating provider event: {}", eventDto.getExternalId());
        Optional<CUSyncCalendarEventEntity> existing = eventRepository
                .findByCuSyncCalendarAndCalendarEventReference(calendar, eventDto.getExternalId());

        CUSyncCalendarEventEntity event = existing.orElse(CUSyncCalendarEventEntity.builder()
                .cuSyncCalendar(calendar)
                .calendarEventReference(eventDto.getExternalId())
                .build());

        eventMapper.updateEntityFromDto(eventDto, event);
        return eventRepository.save(event);
    }

    public Optional<CUSyncCalendarEventEntity> getEventByReference(
            CUSyncCalendarEntity calendar, String externalId) {
        return eventRepository.findByCuSyncCalendarAndCalendarEventReference(calendar, externalId);
    }

    public List<CUSyncCalendarEventEntity> getEventsByDateRange(
            CUSyncCalendarEntity calendar, OffsetDateTime startDate, OffsetDateTime endDate) {
        return eventRepository.findByCuSyncCalendarAndEventStartDateBetweenAndIsCancelledFalse(
                calendar, startDate, endDate);
    }

    public List<CUSyncCalendarEventEntity> getUpcomingEvents(CUSyncCalendarEntity calendar) {
        return eventRepository.findUpcomingEvents(calendar);
    }

    @Transactional
    public void deleteEvent(CUSyncCalendarEventEntity event) {
        log.debug("Deleting provider event: {}", event.getCalendarEventReference());
        guestRepository.findByCuSyncCalendarEvent(event).forEach(guestRepository::delete);
        reminderRepository.findByCuSyncCalendarEvent(event).forEach(reminderRepository::delete);
        eventRepository.delete(event);
    }

    @Transactional
    public void markAsCancelled(CUSyncCalendarEventEntity event) {
        log.debug("Marking event as cancelled: {}", event.getCalendarEventReference());
        calendarEventStatusRepository.findAll().stream()
                .filter(s -> Integer.valueOf(1).equals(s.getIsCancelled()))
                .findFirst()
                .ifPresentOrElse(
                        event::setCalendarEventStatus,
                        () -> log.warn("No CANCELLED CalendarEventStatus found; event not marked cancelled"));
        eventRepository.save(event);
    }

    @Transactional
    public CUSyncCalendarEventGuestEntity addGuest(
            CUSyncCalendarEventEntity event, String guestEmail, String guestName,
            String responseStatus, Boolean isOptional, Boolean isOrganiser) {

        CUSyncCalendarEventGuestEntity guest = CUSyncCalendarEventGuestEntity.builder()
                .cuSyncCalendarEvent(event)
                .guestEmail(guestEmail)
                .guestName(guestName)
                .isOrganizer(Boolean.TRUE.equals(isOrganiser) ? 1 : 0)
                .isOptional(Boolean.TRUE.equals(isOptional) ? 1 : 0)
                .isHuman(1)
                .build();
        guest.setResponseStatus(responseStatus);
        return guestRepository.save(guest);
    }

    @Transactional
    public EventReminderEntity addReminder(
            CUSyncCalendarEventEntity event, String notificationMedium,
            Integer timeValue, Long timeUnitId) {

        EventReminderEntity reminder = EventReminderEntity.builder()
                .cuSyncCalendarEvent(event)
                .notificationMedium(1)
                .timeValue(timeValue)
                .timeUnitId(timeUnitId != null ? timeUnitId.intValue() : 1)
                .build();
        return reminderRepository.save(reminder);
    }

    public long getEventCount(CUSyncCalendarEntity calendar) {
        return eventRepository.countEventsByCalendar(calendar);
    }
}
