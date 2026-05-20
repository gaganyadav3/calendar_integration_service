package com.omvrti.calendar_service.calendar.provider;

import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Generic interface for calendar providers.
 * All methods operate on CustomerUserSyncEntity which consolidates OAuth tokens
 * and sync metadata for a user-provider pair.
 */
public interface ICalendarProvider {

    ProviderType getProviderType();

    /**
     * Fetch list of calendars available for this sync account.
     */
    List<CalendarInfo> fetchCalendars(CustomerUserSyncEntity sync);

    /**
     * Fetch events from a specific calendar since the given time.
     */
    List<EventDto> fetchEvents(CustomerUserSyncEntity sync, String calendarId, OffsetDateTime since);

    /**
     * Fetch all events from a specific calendar (full sync).
     */
    List<EventDto> fetchAllEvents(CustomerUserSyncEntity sync, String calendarId);

    /**
     * Incremental sync using a sync token (Google) or delta link (Outlook).
     * Pass null syncToken for a full sync that returns the initial token.
     * Returns null if the provider does not support token-based sync.
     */
    default SyncFetchResult fetchEventsWithToken(CustomerUserSyncEntity sync, String calendarId, String syncToken) {
        return null;
    }

    /**
     * Create a new event in the provider's calendar.
     * @return External event ID assigned by the provider.
     */
    String createEvent(CustomerUserSyncEntity sync, String calendarId, EventDto event);

    /**
     * Update an existing provider event.
     */
    void updateEvent(CustomerUserSyncEntity sync, String calendarId, String externalEventId, EventDto event);

    /**
     * Delete a provider event.
     */
    void deleteEvent(CustomerUserSyncEntity sync, String calendarId, String externalEventId);

    /**
     * Fetch a single event by its external provider ID.
     */
    EventDto getEvent(CustomerUserSyncEntity sync, String calendarId, String externalEventId);

    /**
     * Resolve user email from access token (used during OAuth flow).
     */
    String getUserEmail(String accessToken);

    /**
     * Parse a raw provider event object into our EventDto format.
     */
    EventDto parseEvent(Object providerEvent);

    /**
     * Get calendar statistics summary.
     */
    default CalendarStateSummary getCurrentStateSummary(CustomerUserSyncEntity sync, String calendarId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<EventDto> all = fetchAllEvents(sync, calendarId);
        long total = all.stream().filter(e -> !e.isCancelled()).count();
        long upcoming = all.stream().filter(e -> !e.isCancelled() && e.getEndTime() != null && e.getEndTime().isAfter(now)).count();
        return new CalendarStateSummary(total, upcoming, now);
    }

    // ── Webhook methods (optional; return null / no-op by default) ───────────

    /**
     * Register a push-notification webhook for a calendar.
     * @return WebhookInfo with the channel/subscription ID and expiry date, or null if unsupported.
     */
    default WebhookInfo registerWebhook(CustomerUserSyncEntity sync, String calendarId, String callbackUrl) {
        return null;
    }

    /**
     * Renew / extend an existing webhook subscription.
     */
    default WebhookInfo renewWebhook(CustomerUserSyncEntity sync, String channelId, String calendarId, String callbackUrl) {
        return null;
    }

    /**
     * Cancel / stop an existing webhook subscription.
     */
    default void deleteWebhook(CustomerUserSyncEntity sync, String channelId, String resourceId) {}

    // ── Records ──────────────────────────────────────────────────────────────

    record CalendarInfo(
        String id,
        String name,
        String color,
        String timeZone,
        boolean isPrimary,
        boolean isWritable
    ) {}

    record WebhookInfo(
        String channelId,
        String resourceId,
        OffsetDateTime expiryDate
    ) {}

    record CalendarStateSummary(
        long totalEvents,
        long totalBookings,
        OffsetDateTime lastFetchTime
    ) {}

    record SyncFetchResult(
        List<EventDto> events,
        String nextSyncToken
    ) {}
}
