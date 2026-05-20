package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.calendar.factory.CalendarProviderFactory;
import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.common.dto.CalendarEventDto;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.CalendarException;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.*;
import com.omvrti.calendar_service.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarService {

    private final CalendarProviderFactory providerFactory;
    private final CustomerUserSyncService customerUserSyncService;
    private final CustomerUserRepository customerUserRepository;
    private final TokenRefreshService tokenRefreshService;
    private final EventManagementService eventManagementService;
    private final ProviderCalendarService providerCalendarService;
    private final ProviderEventService providerEventService;

    // ── Fetch events from provider ────────────────────────────────────────────

    public List<CalendarEventDto> fetchEvents(String userEmail, ProviderType provider) {
        CustomerUserSyncEntity sync = requireSync(userEmail, provider);
        tokenRefreshService.getValidAccessToken(sync, provider);

        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);
        String calendarId = getPrimaryCalendarId(sync);

        List<EventDto> remote = calendarProvider.fetchAllEvents(sync, calendarId).stream()
                .filter(e -> e.getStatus() == null || !"cancelled".equalsIgnoreCase(e.getStatus()))
                .collect(Collectors.toList());

        CUSyncCalendarEntity calendar = providerCalendarService.getOrCreatePrimaryCalendar(sync, calendarId);
        remote.forEach(e -> {
            e.setSource(EventSource.valueOf(provider.name()));
            providerEventService.createOrUpdateEvent(calendar, e);
        });

        log.info("Fetched {} confirmed events from {} for {}", remote.size(), provider, userEmail);
        return remote.stream().map(CalendarService::toLegacyDto).collect(Collectors.toList());
    }

    // ── Create event ──────────────────────────────────────────────────────────

    @Transactional
    public String saveEvent(String userEmail, ProviderType provider, CalendarEventDto eventDto) {
        CustomerUserSyncEntity sync = requireSync(userEmail, provider);
        tokenRefreshService.getValidAccessToken(sync, provider);

        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);
        String calendarId = getPrimaryCalendarId(sync);

        EventDto unified = fromLegacyDto(eventDto, provider);
        unified.setSource(EventSource.INTERNAL);
        unified.setInternalId(UUID.randomUUID().toString());

        String externalId = calendarProvider.createEvent(sync, calendarId, unified);
        unified.setExternalId(externalId);

        CUSyncCalendarEntity calendar = providerCalendarService.getOrCreatePrimaryCalendar(sync, calendarId);
        providerEventService.createOrUpdateEvent(calendar, unified);

        log.info("Created event {} in {} for {}", externalId, provider, userEmail);
        return externalId;
    }

    @Transactional
    public void saveEvents(String userEmail, ProviderType provider, List<CalendarEventDto> events) {
        for (CalendarEventDto dto : events) {
            try { saveEvent(userEmail, provider, dto); }
            catch (Exception e) { log.warn("Failed to save event {}: {}", dto.getSummary(), e.getMessage()); }
        }
    }

    // ── Update event ──────────────────────────────────────────────────────────

    @Transactional
    public void updateEvent(String userEmail, ProviderType provider, String eventId, CalendarEventDto eventDto) {
        CustomerUserSyncEntity sync = requireSync(userEmail, provider);
        tokenRefreshService.getValidAccessToken(sync, provider);

        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);
        String calendarId = getPrimaryCalendarId(sync);

        EventDto unified = fromLegacyDto(eventDto, provider);
        unified.setExternalId(eventId);
        unified.setSource(EventSource.INTERNAL);

        calendarProvider.updateEvent(sync, calendarId, eventId, unified);

        CUSyncCalendarEntity calendar = providerCalendarService.getOrCreatePrimaryCalendar(sync, calendarId);
        providerEventService.createOrUpdateEvent(calendar, unified);

        log.info("Updated event {} in {} for {}", eventId, provider, userEmail);
    }

    // ── Delete event ──────────────────────────────────────────────────────────

    @Transactional
    public void deleteEvent(String userEmail, ProviderType provider, String eventId) {
        CustomerUserSyncEntity sync = requireSync(userEmail, provider);
        tokenRefreshService.getValidAccessToken(sync, provider);

        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);
        String calendarId = getPrimaryCalendarId(sync);
        calendarProvider.deleteEvent(sync, calendarId, eventId);

        CUSyncCalendarEntity calendar = providerCalendarService.getOrCreatePrimaryCalendar(sync, calendarId);
        providerEventService.getEventByReference(calendar, eventId)
                .ifPresent(providerEventService::markAsCancelled);

        log.info("Deleted event {} in {} for {}", eventId, provider, userEmail);
    }

    // ── Read operations ───────────────────────────────────────────────────────

    public CalendarEventDto getEvent(String userEmail, ProviderType provider, String eventId) {
        CustomerUserSyncEntity sync = requireSync(userEmail, provider);
        tokenRefreshService.getValidAccessToken(sync, provider);
        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);
        EventDto unified = calendarProvider.getEvent(sync, getPrimaryCalendarId(sync), eventId);
        return toLegacyDto(unified);
    }

    public List<CUSyncCalendarEventEntity> getUserEvents(String userEmail) {
        return eventManagementService.getUserEvents(userEmail);
    }

    public List<CUSyncCalendarEventEntity> getUserEventsByProvider(String userEmail, ProviderType provider) {
        return eventManagementService.getEventsByProvider(userEmail, provider);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CustomerUserSyncEntity requireSync(String userEmail, ProviderType provider) {
        return customerUserSyncService.getSyncByEmail(userEmail, provider)
                .filter(s -> Integer.valueOf(1).equals(s.getIsActive()))
                .orElseThrow(() -> new CalendarException("ACCOUNT_NOT_CONNECTED",
                        "Provider not connected: " + provider + " for user: " + userEmail));
    }

    private String getPrimaryCalendarId(CustomerUserSyncEntity sync) {
        return providerCalendarService.getPrimaryCalendar(sync)
                .map(CUSyncCalendarEntity::getCalendarReference)
                .orElse("primary");
    }

    private static CalendarEventDto toLegacyDto(EventDto e) {
        return CalendarEventDto.builder()
                .id(e.getExternalId())
                .summary(e.getTitle())
                .description(e.getDescription())
                .location(e.getLocation())
                .organizer(e.getOrganizer())
                .status(e.getStatus())
                .allDay(e.isAllDay())
                .startDateTime(e.getStartTime())
                .endDateTime(e.getEndTime())
                .build();
    }

    private static EventDto fromLegacyDto(CalendarEventDto dto, ProviderType provider) {
        OffsetDateTime start = dto.getStartDateTime();
        OffsetDateTime end = dto.getEndDateTime();
        if (dto.isAllDay() && dto.getStartDate() != null)
            start = dto.getStartDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        if (dto.isAllDay() && dto.getEndDate() != null)
            end = dto.getEndDate().atStartOfDay().atOffset(ZoneOffset.UTC);

        return EventDto.builder()
                .externalId(dto.getId())
                .title(dto.getSummary())
                .description(dto.getDescription())
                .location(dto.getLocation())
                .organizer(dto.getOrganizer())
                .startTime(start).endTime(end)
                .timeZoneId("UTC")
                .allDay(dto.isAllDay())
                .status(dto.getStatus())
                .provider(provider)
                .build();
    }
}
