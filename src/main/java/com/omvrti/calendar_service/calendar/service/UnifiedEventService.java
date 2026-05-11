package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.calendar.factory.CalendarProviderFactory;
import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import com.omvrti.calendar_service.persistence.entity.EventEntity;
import com.omvrti.calendar_service.persistence.entity.UserEntity;
import com.omvrti.calendar_service.persistence.repository.ConnectedAccountRepository;
import com.omvrti.calendar_service.persistence.repository.EventRepository;
import com.omvrti.calendar_service.persistence.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final ConnectedAccountRepository accountRepository;
    private final EventRepository eventRepository;
    private final EventManagementService eventManagementService;
    private final TokenRefreshService tokenRefreshService;
    private final CalendarProviderFactory providerFactory;

    @Transactional
    public EventDto create(String userEmail, EventDto request) {
        ProviderType provider = requireProvider(request.getProvider());
        ConnectedAccountEntity account = requireAccount(userEmail, provider);

        tokenRefreshService.getValidAccessToken(userEmail, provider);
        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);

        EventDto dto = normalizeToUtc(request);
        dto.setInternalId(dto.getInternalId() != null ? dto.getInternalId() : UUID.randomUUID().toString());
        dto.setProvider(provider);
        dto.setSource(EventSource.INTERNAL);

        String externalId = calendarProvider.createEvent(account, dto);
        dto.setExternalId(externalId);

        EventEntity saved = eventManagementService.saveEvent(userEmail, dto, account);
        return toDto(saved);
    }

    public List<EventDto> list(String userEmail) {
        return eventManagementService.getUserEvents(userEmail).stream().map(UnifiedEventService::toDto).collect(Collectors.toList());
    }

    public Optional<EventDto> get(String internalId) {
        return eventRepository.findByInternalId(internalId).map(UnifiedEventService::toDto);
    }

    @Transactional
    public EventDto update(String userEmail, String internalId, EventDto request) {
        EventEntity existing = eventRepository.findByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + internalId));

        ProviderType provider = requireProvider(existing.getProvider() != null ? existing.getProvider() : request.getProvider());
        ConnectedAccountEntity account = requireAccount(userEmail, provider);
        tokenRefreshService.getValidAccessToken(userEmail, provider);

        ICalendarProvider calendarProvider = providerFactory.getProvider(provider);

        EventDto dto = normalizeToUtc(request);
        dto.setInternalId(existing.getInternalId());
        dto.setExternalId(existing.getExternalId());
        dto.setProvider(provider);
        dto.setSource(EventSource.INTERNAL);

        if (existing.getExternalId() == null) {
            String externalId = calendarProvider.createEvent(account, dto);
            dto.setExternalId(externalId);
        } else {
            calendarProvider.updateEvent(account, existing.getExternalId(), dto);
        }

        EventEntity saved = eventManagementService.saveEvent(userEmail, dto, account);
        return toDto(saved);
    }

    @Transactional
    public void delete(String userEmail, String internalId) {
        EventEntity existing = eventRepository.findByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + internalId));

        if (existing.getProvider() != null && existing.getExternalId() != null) {
            ConnectedAccountEntity account = requireAccount(userEmail, existing.getProvider());
            tokenRefreshService.getValidAccessToken(userEmail, existing.getProvider());
            ICalendarProvider calendarProvider = providerFactory.getProvider(existing.getProvider());
            calendarProvider.deleteEvent(account, existing.getExternalId());
        }

        eventManagementService.deleteEvent(internalId);
    }

    private ConnectedAccountEntity requireAccount(String userEmail, ProviderType provider) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return accountRepository.findByUserAndProvider(user, provider)
                .filter(ConnectedAccountEntity::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Provider not connected/active: " + provider));
    }

    private static ProviderType requireProvider(ProviderType provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider is required");
        }
        return provider;
    }

    private static EventDto normalizeToUtc(EventDto in) {
        EventDto.EventDtoBuilder b = EventDto.builder()
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
                .externalUpdatedAt(in.getExternalUpdatedAt());

        OffsetDateTime start = in.getStartTime();
        OffsetDateTime end = in.getEndTime();
        if (start != null) start = start.withOffsetSameInstant(ZoneOffset.UTC);
        if (end != null) end = end.withOffsetSameInstant(ZoneOffset.UTC);

        b.startTime(start);
        b.endTime(end);
        b.timeZoneId("UTC");
        return b.build();
    }

    private static EventDto toDto(EventEntity e) {
        return EventDto.builder()
                .id(e.getId())
                .internalId(e.getInternalId())
                .externalId(e.getExternalId())
                .title(e.getTitle())
                .description(e.getDescription())
                .location(e.getLocation())
                .organizer(e.getOrganizer())
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .timeZoneId("UTC")
                .allDay(e.isAllDay())
                .status(e.getStatus())
                .isCancelled(e.isCancelled())
                .provider(e.getProvider())
                .source(e.getSource())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .externalUpdatedAt(e.getExternalUpdatedAt())
                .syncStatus(e.getSyncStatus())
                .version(e.getVersion())
                .build();
    }
}

