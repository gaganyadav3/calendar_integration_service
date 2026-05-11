package com.omvrti.calendar_service.calendar.provider;

import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Generic interface for calendar providers
 * Abstracts away provider-specific API details
 */
public interface ICalendarProvider {
    
    /**
     * Get the provider type
     */
    ProviderType getProviderType();
    
    /**
     * Fetch events since last sync
     * @param account Connected account with auth details
     * @param since Last sync time (for incremental sync)
     * @return List of events from provider
     */
    List<EventDto> fetchEvents(ConnectedAccountEntity account, OffsetDateTime since);

    /**
     * Fetch all events (full sync)
     */
    List<EventDto> fetchAllEvents(ConnectedAccountEntity account);

    /**
     * Create a new event in the provider's calendar
     * @return External event ID from provider
     */
    String createEvent(ConnectedAccountEntity account, EventDto event);

    /**
     * Update an existing event in provider's calendar
     */
    void updateEvent(ConnectedAccountEntity account, String externalEventId, EventDto event);

    /**
     * Delete an event from provider's calendar
     */
    void deleteEvent(ConnectedAccountEntity account, String externalEventId);

    /**
     * Get a single event by external ID
     */
    EventDto getEvent(ConnectedAccountEntity account, String externalEventId);

    /**
     * Get user profile information
     */
    String getUserEmail(String accessToken);

    /**
     * Parse provider's event format to our internal EventDto format
     */
    EventDto parseEvent(Object providerEvent);

    /**
     * Get current state summary
     */
    CalendarStateSummary getCurrentStateSummary(ConnectedAccountEntity account);

    /**
     * Fetch events using a sync token for incremental sync.
     * Pass null syncToken to perform a full sync and receive the initial token.
     * Returns null if the provider does not support sync tokens.
     */
    default SyncFetchResult fetchEventsWithToken(ConnectedAccountEntity account, String syncToken) {
        return null;
    }

    /**
     * Calendar state summary with counts
     */
    record CalendarStateSummary(
        long totalEvents,
        long totalBookings,
        OffsetDateTime lastFetchTime
    ) {}

    /**
     * Result of a sync-token-based fetch, carrying both events and the next token.
     */
    record SyncFetchResult(List<EventDto> events, String nextSyncToken) {}
}

