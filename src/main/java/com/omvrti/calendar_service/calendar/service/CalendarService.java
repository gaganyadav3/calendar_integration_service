package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.calendar.factory.CalendarProviderFactory;
import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.common.dto.CalendarEventDto;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import com.omvrti.calendar_service.persistence.entity.EventEntity;
import com.omvrti.calendar_service.persistence.entity.UserEntity;
import com.omvrti.calendar_service.persistence.repository.ConnectedAccountRepository;
import com.omvrti.calendar_service.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backwards-compatible facade used by the existing /api/calendar endpoints.
 *
 * Internally, this delegates to the unified provider + EventEntity model.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarService {

    private final CalendarProviderFactory providerFactory;
    private final UserRepository userRepository;
    private final ConnectedAccountRepository accountRepository;
    private final TokenRefreshService tokenRefreshService;
    private final EventManagementService eventManagementService;

    public List<CalendarEventDto> fetchEvents(String userEmail, ProviderType provider) {
        ConnectedAccountEntity account = requireAccount(userEmail, provider);
        tokenRefreshService.getValidAccessToken(userEmail, provider);

        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);
        List<EventDto> remote = calendarProvider.fetchAllEvents(account);

        // 🔥 Filter only confirmed events
        List<EventDto> filtered = remote.stream()
                .filter(e -> e.getStatus() != null
                        && "confirmed".equalsIgnoreCase(e.getStatus()))
                .collect(Collectors.toList());

        // Persist only confirmed events
        filtered.forEach(e -> {
            e.setSource(EventSource.valueOf(provider.name()));
            eventManagementService.saveEvent(userEmail, e, account);
        });

        // Return only confirmed events
        return filtered.stream()
                .map(CalendarService::toLegacyDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public String saveEvent(String userEmail, ProviderType provider, CalendarEventDto eventDto) {
        ConnectedAccountEntity account = requireAccount(userEmail, provider);
        tokenRefreshService.getValidAccessToken(userEmail, provider);

        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);

        EventDto unified = fromLegacyDto(eventDto, provider);
        unified.setSource(EventSource.INTERNAL);
        unified.setInternalId(unified.getInternalId() != null ? unified.getInternalId() : UUID.randomUUID().toString());

        String externalId = calendarProvider.createEvent(account, unified);
        unified.setExternalId(externalId);

        eventManagementService.saveEvent(userEmail, unified, account);
        return externalId;
    }

    @Transactional
    public void saveEvents(String userEmail, ProviderType provider, List<CalendarEventDto> events) {
        for (CalendarEventDto dto : events) {
            try {
                saveEvent(userEmail, provider, dto);
            } catch (Exception e) {
                log.warn("Failed to save legacy event {}", dto.getSummary(), e);
            }
        }
    }

    @Transactional
    public void updateEvent(String userEmail, ProviderType provider, String eventId, CalendarEventDto eventDto) {
        ConnectedAccountEntity account = requireAccount(userEmail, provider);
        tokenRefreshService.getValidAccessToken(userEmail, provider);

        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);

        EventDto unified = fromLegacyDto(eventDto, provider);
        unified.setExternalId(eventId);
        unified.setSource(EventSource.INTERNAL);

        calendarProvider.updateEvent(account, eventId, unified);
        eventManagementService.saveEvent(userEmail, unified, account);
    }

    @Transactional
    public void deleteEvent(String userEmail, ProviderType provider, String eventId) {
        ConnectedAccountEntity account = requireAccount(userEmail, provider);
        tokenRefreshService.getValidAccessToken(userEmail, provider);

        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);
        calendarProvider.deleteEvent(account, eventId);

        // Best-effort: mark matching events as deleted in unified table.
        List<EventEntity> events = eventManagementService.findByExternalIdAndProvider(eventId, provider);
        for (EventEntity e : events) {
            eventManagementService.deleteEvent(e.getInternalId());
        }
    }

    public CalendarEventDto getEvent(String userEmail, ProviderType provider, String eventId) {
        ConnectedAccountEntity account = requireAccount(userEmail, provider);
        tokenRefreshService.getValidAccessToken(userEmail, provider);

        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);
        EventDto unified = calendarProvider.getEvent(account, eventId);
        return toLegacyDto(unified);
    }

    public List<EventEntity> getUserEvents(String userEmail) {
        return eventManagementService.getUserEvents(userEmail);
    }

    public List<EventEntity> getUserEventsByProvider(String userEmail, ProviderType provider) {
        return eventManagementService.getEventsByProvider(userEmail, provider);
    }

    private ConnectedAccountEntity requireAccount(String userEmail, ProviderType provider) {
        log.debug("Resolving connected account for user {} and provider {}", userEmail, provider);
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        ConnectedAccountEntity account = accountRepository.findByUserAndProvider(user, provider)
                .filter(ConnectedAccountEntity::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Provider not connected/active: " + provider));
        log.debug("Resolved connected account id {} with provider {}", account.getId(), account.getProvider());
        return account;
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
        if (dto.isAllDay() && dto.getStartDate() != null) {
            start = dto.getStartDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        if (dto.isAllDay() && dto.getEndDate() != null) {
            end = dto.getEndDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        return EventDto.builder()
                .externalId(dto.getId())
                .title(dto.getSummary())
                .description(dto.getDescription())
                .location(dto.getLocation())
                .organizer(dto.getOrganizer())
                .startTime(start)
                .endTime(end)
                .timeZoneId("UTC")
                .allDay(dto.isAllDay())
                .status(dto.getStatus())
                .provider(provider)
                .build();
    }
}
