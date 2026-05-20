package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.calendar.factory.CalendarProviderFactory;
import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.util.EventEntityMapper;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEntity;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEventEntity;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.repository.CUSyncCalendarEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedEventService {

    private final CUSyncCalendarEventRepository eventRepository;
    private final EventManagementService eventManagementService;
    private final TokenRefreshService tokenRefreshService;
    private final CalendarProviderFactory providerFactory;
    private final CustomerUserSyncService customerUserSyncService;
    private final ProviderCalendarService providerCalendarService;
    private final EventEntityMapper eventMapper;

    @Transactional
    public EventDto create(String userEmail, EventDto request) {
        ProviderType provider = requireProvider(request.getProvider());
        CustomerUserSyncEntity sync = requireSync(userEmail, provider);
        tokenRefreshService.getValidAccessToken(sync, provider);

        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);
        EventDto dto = normalizeToUtc(request);
        dto.setInternalId(dto.getInternalId() != null ? dto.getInternalId() : UUID.randomUUID().toString());
        dto.setProvider(provider);
        dto.setSource(EventSource.INTERNAL);

        String externalId = calendarProvider.createEvent(sync, "primary", dto);
        dto.setExternalId(externalId);

        CUSyncCalendarEntity calendar = providerCalendarService.getOrCreatePrimaryCalendar(sync, "primary");
        CUSyncCalendarEventEntity saved = eventManagementService.saveEvent(userEmail, dto, calendar);
        return eventMapper.entityToDto(saved);
    }

    public List<EventDto> list(String userEmail) {
        return eventManagementService.getUserEvents(userEmail).stream()
                .map(eventMapper::entityToDto)
                .collect(Collectors.toList());
    }

    public Optional<EventDto> get(String calendarEventReference) {
        return eventRepository.findByCalendarEventReference(calendarEventReference)
                .stream().findFirst()
                .map(eventMapper::entityToDto);
    }

    @Transactional
    public EventDto update(String userEmail, String calendarEventReference, EventDto request) {
        CUSyncCalendarEventEntity existing = eventRepository
                .findByCalendarEventReference(calendarEventReference)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + calendarEventReference));

        ProviderType provider = requireProvider(
                existing.getCalendarEventReference() != null ? request.getProvider() : request.getProvider());
        CustomerUserSyncEntity sync = requireSync(userEmail, provider);
        tokenRefreshService.getValidAccessToken(sync, provider);

        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);
        EventDto dto = normalizeToUtc(request);
        dto.setExternalId(calendarEventReference);
        dto.setProvider(provider);
        dto.setSource(EventSource.INTERNAL);

        calendarProvider.updateEvent(sync, "primary", calendarEventReference, dto);

        CUSyncCalendarEntity calendar = existing.getCuSyncCalendar();
        CUSyncCalendarEventEntity savedEntity = eventManagementService.saveEvent(userEmail, dto, calendar);
        return eventMapper.entityToDto(savedEntity);
    }

    @Transactional
    public void delete(String userEmail, String calendarEventReference) {
        CUSyncCalendarEventEntity existing = eventRepository
                .findByCalendarEventReference(calendarEventReference)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + calendarEventReference));

        if (existing.getCalendarEventReference() != null) {
            try {
                // Try to find the provider from the calendar's sync vendor
                CUSyncCalendarEntity cal = existing.getCuSyncCalendar();
                if (cal != null && cal.getCustomerUserSync() != null
                        && cal.getCustomerUserSync().getSyncVendor() != null) {
                    ProviderType provider = ProviderType.valueOf(
                            cal.getCustomerUserSync().getSyncVendor().getVendorCode());
                    CustomerUserSyncEntity sync = requireSync(userEmail, provider);
                    tokenRefreshService.getValidAccessToken(sync, provider);
                    providerFactory.getProvider(provider)
                            .deleteEvent(sync, "primary", calendarEventReference);
                }
            } catch (Exception e) {
                log.warn("Could not delete event from provider: {}", e.getMessage());
            }
        }
        eventManagementService.deleteEvent(calendarEventReference);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CustomerUserSyncEntity requireSync(String userEmail, ProviderType provider) {
        return customerUserSyncService.getSyncByEmail(userEmail, provider)
                .filter(s -> Integer.valueOf(1).equals(s.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not connected: " + provider + " for user: " + userEmail));
    }

    private static ProviderType requireProvider(ProviderType provider) {
        if (provider == null) throw new IllegalArgumentException("provider is required");
        return provider;
    }

    private static EventDto normalizeToUtc(EventDto in) {
        OffsetDateTime start = in.getStartTime();
        OffsetDateTime end = in.getEndTime();
        if (start != null) start = start.withOffsetSameInstant(ZoneOffset.UTC);
        if (end != null) end = end.withOffsetSameInstant(ZoneOffset.UTC);
        return EventDto.builder()
                .id(in.getId())
                .internalId(in.getInternalId())
                .externalId(in.getExternalId())
                .title(in.getTitle())
                .description(in.getDescription())
                .location(in.getLocation())
                .organizer(in.getOrganizer())
                .allDay(in.isAllDay())
                .status(in.getStatus())
                .isCancelled(in.isCancelled())
                .provider(in.getProvider())
                .source(in.getSource())
                .externalUpdatedAt(in.getExternalUpdatedAt())
                .startTime(start).endTime(end)
                .timeZoneId("UTC")
                .build();
    }
}
