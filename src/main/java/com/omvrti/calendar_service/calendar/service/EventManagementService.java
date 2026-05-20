package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.util.EventEntityMapper;
import com.omvrti.calendar_service.persistence.entity.*;
import com.omvrti.calendar_service.persistence.repository.*;
import com.omvrti.calendar_service.persistence.service.SyncVendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Event management using the new CUSyncCalendar* entity model.
 * All operations go through CUSyncCalendarEventEntity (mapped to CU_SYNC_CALENDAR_EVENT table).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventManagementService {

    private final CustomerUserRepository customerUserRepository;
    private final CustomerUserSyncRepository customerUserSyncRepository;
    private final CUSyncCalendarRepository calendarRepository;
    private final CUSyncCalendarEventRepository eventRepository;
    private final CUSyncCalendarEventGuestRepository guestRepository;
    private final EventReminderRepository reminderRepository;
    private final SyncVendorService syncVendorService;
    private final EventEntityMapper eventMapper;

    // ── Save / upsert ─────────────────────────────────────────────────────────

    @Transactional
    public CUSyncCalendarEventEntity saveEvent(String userEmail, EventDto eventDto,
                                                CUSyncCalendarEntity calendar) {
        log.debug("Saving event: {} for user: {}", eventDto.getTitle(), userEmail);

        Optional<CUSyncCalendarEventEntity> existing = Optional.empty();
        if (eventDto.getExternalId() != null && calendar != null) {
            existing = eventRepository.findByCuSyncCalendarAndCalendarEventReference(
                    calendar, eventDto.getExternalId());
        }

        CUSyncCalendarEventEntity event = existing.orElse(CUSyncCalendarEventEntity.builder()
                .cuSyncCalendar(calendar)
                .calendarEventReference(eventDto.getExternalId())
                .isAllDay(0)
                .isVisible(1)
                .build());

        eventMapper.updateEntityFromDto(eventDto, event);
        return eventRepository.save(event);
    }

    @Transactional
    public List<CUSyncCalendarEventEntity> saveEvents(String userEmail, List<EventDto> events,
                                                       CUSyncCalendarEntity calendar) {
        return events.stream()
                .map(dto -> saveEvent(userEmail, dto, calendar))
                .collect(Collectors.toList());
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteEvent(String calendarEventReference) {
        log.debug("Deleting event by reference: {}", calendarEventReference);
        eventRepository.findByCalendarEventReference(calendarEventReference)
                .forEach(e -> {
                    guestRepository.findByCuSyncCalendarEvent(e).forEach(guestRepository::delete);
                    reminderRepository.findByCuSyncCalendarEvent(e).forEach(reminderRepository::delete);
                    eventRepository.delete(e);
                });
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CUSyncCalendarEventEntity getEvent(String calendarEventReference) {
        return eventRepository.findByCalendarEventReference(calendarEventReference)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + calendarEventReference));
    }

    @Transactional(readOnly = true)
    public List<CUSyncCalendarEventEntity> getUserEvents(String userEmail) {
        log.debug("Fetching events for user: {}", userEmail);
        return customerUserRepository.findByEmail(userEmail)
                .map(user -> {
                    List<CUSyncCalendarEntity> calendars = calendarRepository
                            .findAll().stream()
                            .filter(c -> c.getCustomerUserSync() != null
                                    && c.getCustomerUserSync().getCustomerUser() != null
                                    && user.getCustomerId().equals(
                                            c.getCustomerUserSync().getCustomerUser().getCustomerId()))
                            .collect(Collectors.toList());
                    return calendars.stream()
                            .flatMap(c -> eventRepository.findByCuSyncCalendar(c).stream())
                            .collect(Collectors.toList());
                })
                .orElse(Collections.emptyList());
    }

    @Transactional(readOnly = true)
    public List<CUSyncCalendarEventEntity> getEventsByProvider(String userEmail, ProviderType provider) {
        log.debug("Fetching events for user {} from provider {}", userEmail, provider);
        return customerUserRepository.findByEmail(userEmail)
                .map(user -> {
                    SyncVendorEntity vendor;
                    try { vendor = syncVendorService.getVendor(provider); }
                    catch (Exception e) { return Collections.<CUSyncCalendarEventEntity>emptyList(); }

                    return customerUserSyncRepository
                            .findByCustomerUserAndSyncVendor(user, vendor)
                            .map(sync -> calendarRepository.findByCustomerUserSync(sync).stream()
                                    .flatMap(c -> eventRepository.findByCuSyncCalendar(c).stream())
                                    .collect(Collectors.toList()))
                            .orElse(Collections.<CUSyncCalendarEventEntity>emptyList());
                })
                .orElse(Collections.emptyList());
    }

    @Transactional(readOnly = true)
    public List<CUSyncCalendarEventEntity> getEventsInRange(String userEmail,
                                                              OffsetDateTime start, OffsetDateTime end) {
        return getUserEvents(userEmail).stream()
                .filter(e -> e.getEventStartDate() != null
                        && !e.getEventStartDate().isBefore(start)
                        && (e.getEventEndDate() == null || !e.getEventEndDate().isAfter(end)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CUSyncCalendarEventEntity> getUpcomingEvents(String userEmail) {
        return getUserEvents(userEmail).stream()
                .filter(e -> e.getEventEndDate() != null
                        && e.getEventEndDate().isAfter(OffsetDateTime.now()))
                .collect(Collectors.toList());
    }

    public long countEvents(String userEmail, ProviderType provider) {
        return getEventsByProvider(userEmail, provider).size();
    }
}
