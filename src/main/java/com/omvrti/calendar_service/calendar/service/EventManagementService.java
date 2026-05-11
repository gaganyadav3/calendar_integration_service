package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.persistence.entity.EventEntity;
import com.omvrti.calendar_service.persistence.entity.UserEntity;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import com.omvrti.calendar_service.persistence.repository.EventRepository;
import com.omvrti.calendar_service.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing calendar events
 * Handles CRUD operations on events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventManagementService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    /**
     * Create or update event in database
     */
    @Transactional
    public EventEntity saveEvent(String userEmail, EventDto eventDto, ConnectedAccountEntity account) {
        log.debug("Saving event: {} for user: {}", eventDto.getTitle(), userEmail);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        // Generate internal ID if not present
        String internalId = eventDto.getInternalId() != null ?
            eventDto.getInternalId() :
            generateInternalId(userEmail, eventDto);

        // Check if event exists
        Optional<EventEntity> existing = eventRepository.findByInternalId(internalId);

        EventEntity event = existing.orElse(new EventEntity());
        event.setUser(user);
        event.setInternalId(internalId);
        event.setExternalId(eventDto.getExternalId());

        if (account != null) {
            event.setConnectedAccount(account);
            event.setProvider(account.getProvider());
        }

        event.setTitle(eventDto.getTitle());
        event.setDescription(eventDto.getDescription());
        event.setLocation(eventDto.getLocation());
        event.setStartTime(eventDto.getStartTime());
        event.setEndTime(eventDto.getEndTime());
        event.setTimeZoneId(eventDto.getTimeZoneId());
        event.setAllDay(eventDto.isAllDay());
        event.setStatus(eventDto.getStatus());
        event.setOrganizer(eventDto.getOrganizer());
        event.setCancelled(eventDto.isCancelled());
        event.setSource(eventDto.getSource() != null ? eventDto.getSource() : EventSource.INTERNAL);
        event.setExternalUpdatedAt(eventDto.getExternalUpdatedAt());
        event.setDeleted(false);
        long currentVersion = event.getVersion() == null ? 0L : event.getVersion();
        event.setVersion(currentVersion + 1);

        EventEntity saved = eventRepository.save(event);
        log.debug("Event saved: {}", saved.getInternalId());
        return saved;
    }

    /**
     * Save multiple events in bulk
     */
    @Transactional
    public List<EventEntity> saveEvents(String userEmail, List<EventDto> events, ConnectedAccountEntity account) {
        log.info("Saving {} events for user: {}", events.size(), userEmail);
        return events.stream()
                .map(dto -> saveEvent(userEmail, dto, account))
                .collect(Collectors.toList());
    }

    /**
     * Mark event as deleted (soft delete)
     */
    @Transactional
    public void deleteEvent(String internalId) {
        log.debug("Soft-deleting event: {}", internalId);

        EventEntity event = eventRepository.findByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + internalId));

        event.setDeleted(true);
        eventRepository.save(event);
        log.debug("Event marked as deleted: {}", internalId);
    }

    /**
     * Get event by internal ID
     */
    public EventEntity getEvent(String internalId) {
        return eventRepository.findByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + internalId));
    }

    /**
     * Get all non-deleted events for user
     */
    public List<EventEntity> getUserEvents(String userEmail) {
        log.debug("Fetching events for user: {}", userEmail);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return eventRepository.findByUserAndIsDeletedFalse(user);
    }

    /**
     * Get events in date range
     */
    public List<EventEntity> getEventsInRange(String userEmail, OffsetDateTime start, OffsetDateTime end) {
        log.debug("Fetching events for user {} in range {} - {}", userEmail, start, end);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return eventRepository.findByUserAndStartTimeBetweenAndIsDeletedFalse(user, start, end);
    }

    /**
     * Get upcoming bookings (non-cancelled, future events)
     */
    public List<EventEntity> getUpcomingBookings(String userEmail) {
        log.debug("Fetching upcoming bookings for user: {}", userEmail);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return eventRepository.findUpcomingBookings(user);
    }

    /**
     * Get events by provider
     */
    public List<EventEntity> getEventsByProvider(String userEmail, ProviderType provider) {
        log.debug("Fetching events for user {} from provider {}", userEmail, provider);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return eventRepository.findByUserAndProviderAndIsDeletedFalse(user, provider);
    }

    /**
     * Find events by external ID and provider
     */
    public List<EventEntity> findByExternalIdAndProvider(String externalId, ProviderType provider) {
        return eventRepository.findByExternalIdAndProvider(externalId, provider);
    }

    /**
     * Count total events for user and provider
     */
    public long countEvents(String userEmail, ProviderType provider) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return eventRepository.countEventsByUserAndProvider(user, provider);
    }

    /**
     * Count upcoming bookings for user and provider
     */
    public long countUpcomingBookings(String userEmail, ProviderType provider) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return eventRepository.countUpcomingBookingsByUserAndProvider(user, provider);
    }

    /**
     * Get recently updated events for sync
     */
    public List<EventEntity> getRecentlyUpdatedEvents(String userEmail, ProviderType provider, OffsetDateTime since) {
        log.debug("Fetching recently updated events for user {} from {} since {}", userEmail, provider, since);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return eventRepository.findByUserAndProviderAndUpdatedAtAfterAndIsDeletedFalse(user, provider, since);
    }

    private String generateInternalId(String userEmail, EventDto event) {
        return UUID.nameUUIDFromBytes(
            (userEmail + event.getTitle() + event.getStartTime()).getBytes()
        ).toString();
    }
}
