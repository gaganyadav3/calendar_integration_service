package com.omvrti.calendar_service.calendar.provider.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.common.dto.AttendeeDto;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.dto.ReminderDto;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.CalendarException;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Google Calendar provider — uses the official Google Calendar Java client library.
 * Tokens are refreshed by TokenRefreshService before every call.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarProvider implements ICalendarProvider {

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String PRIMARY = "primary";

    private final ObjectMapper objectMapper;

    @Value("${webhook.callback.base-url:}")
    private String webhookBaseUrl;

    // ─────────────────────────────────────────────────────────────────────────
    // Core identity
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ProviderType getProviderType() {
        return ProviderType.GOOGLE;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Calendar list
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<CalendarInfo> fetchCalendars(CustomerUserSyncEntity sync) {
        log.debug("Fetching Google calendar list for {}", sync.getSyncEmail());
        try {
            Calendar service = buildService(sync.getAccessToken());
            CalendarList list = service.calendarList().list().execute();
            List<CalendarInfo> result = new ArrayList<>();
            for (CalendarListEntry entry : safe(list.getItems())) {
                boolean isWritable = "owner".equals(entry.getAccessRole())
                        || "writer".equals(entry.getAccessRole());
                result.add(new CalendarInfo(
                        entry.getId(),
                        entry.getSummary() != null ? entry.getSummary() : entry.getId(),
                        entry.getBackgroundColor(),
                        entry.getTimeZone(),
                        Boolean.TRUE.equals(entry.isPrimary()),
                        isWritable
                ));
            }
            log.info("Fetched {} Google calendars for {}", result.size(), sync.getSyncEmail());
            return result;
        } catch (Exception e) {
            throw new CalendarException("FETCH_CALENDARS_FAILED",
                    "Failed to fetch Google calendar list: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event fetching
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<EventDto> fetchEvents(CustomerUserSyncEntity sync, String calendarId, OffsetDateTime since) {
        SyncFetchResult result = fetchEventsWithToken(sync, calendarId, null);
        if (result != null) return result.events();
        return Collections.emptyList();
    }

    @Override
    public List<EventDto> fetchAllEvents(CustomerUserSyncEntity sync, String calendarId) {
        String id = calendarId != null ? calendarId : PRIMARY;
        try {
            Calendar service = buildService(sync.getAccessToken());
            Events result = service.events().list(id)
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setMaxResults(2500)
                    .setShowDeleted(true)
                    .execute();
            List<EventDto> out = new ArrayList<>();
            for (Event e : safe(result.getItems())) out.add(parseGoogleEvent(e));
            return out;
        } catch (Exception e) {
            throw new CalendarException("FETCH_EVENTS_FAILED",
                    "Failed to fetch Google events: " + e.getMessage(), e);
        }
    }

    @Override
    public SyncFetchResult fetchEventsWithToken(CustomerUserSyncEntity sync, String calendarId, String syncToken) {
        String id = calendarId != null ? calendarId : PRIMARY;
        log.debug("Google incremental sync for calendar {} syncToken={}", id, syncToken != null ? "present" : "null");
        try {
            Calendar service = buildService(sync.getAccessToken());
            List<EventDto> allEvents = new ArrayList<>();
            String pageToken = null;
            String nextSyncToken = null;

            do {
                Calendar.Events.List request = service.events()
                        .list(id)
                        .setSingleEvents(true)
                        .setMaxResults(2500)
                        .setShowDeleted(true);

                if (syncToken != null) request.setSyncToken(syncToken);
                if (pageToken != null) request.setPageToken(pageToken);

                Events result = request.execute();
                for (Event event : safe(result.getItems())) {
                    allEvents.add(parseGoogleEvent(event));
                }
                pageToken = result.getNextPageToken();
                if (pageToken == null) nextSyncToken = result.getNextSyncToken();

            } while (pageToken != null);

            log.info("Google sync complete: {} events fetched for {}", allEvents.size(), sync.getSyncEmail());
            return new SyncFetchResult(allEvents, nextSyncToken);

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 410) {
                throw new CalendarException("SYNC_TOKEN_EXPIRED",
                        "Google sync token expired (410 Gone) — full resync required", e);
            }
            throw new CalendarException("FETCH_EVENTS_FAILED",
                    "Google Calendar API error: " + e.getMessage(), e);
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            throw new CalendarException("FETCH_EVENTS_FAILED",
                    "Failed to fetch Google events: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String createEvent(CustomerUserSyncEntity sync, String calendarId, EventDto event) {
        String id = calendarId != null ? calendarId : PRIMARY;
        try {
            Calendar service = buildService(sync.getAccessToken());
            Event googleEvent = toGoogleEvent(event);
            Event created = service.events().insert(id, googleEvent).execute();
            log.info("Created Google event {} in calendar {}", created.getId(), id);
            return created.getId();
        } catch (Exception e) {
            throw new CalendarException("CREATE_EVENT_FAILED",
                    "Failed to create Google event: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateEvent(CustomerUserSyncEntity sync, String calendarId, String externalEventId, EventDto event) {
        String id = calendarId != null ? calendarId : PRIMARY;
        try {
            Calendar service = buildService(sync.getAccessToken());
            Event googleEvent = toGoogleEvent(event);
            service.events().update(id, externalEventId, googleEvent).execute();
            log.info("Updated Google event {} in calendar {}", externalEventId, id);
        } catch (Exception e) {
            throw new CalendarException("UPDATE_EVENT_FAILED",
                    "Failed to update Google event: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteEvent(CustomerUserSyncEntity sync, String calendarId, String externalEventId) {
        String id = calendarId != null ? calendarId : PRIMARY;
        try {
            Calendar service = buildService(sync.getAccessToken());
            service.events().delete(id, externalEventId).execute();
            log.info("Deleted Google event {} from calendar {}", externalEventId, id);
        } catch (Exception e) {
            throw new CalendarException("DELETE_EVENT_FAILED",
                    "Failed to delete Google event: " + e.getMessage(), e);
        }
    }

    @Override
    public EventDto getEvent(CustomerUserSyncEntity sync, String calendarId, String externalEventId) {
        String id = calendarId != null ? calendarId : PRIMARY;
        try {
            Calendar service = buildService(sync.getAccessToken());
            Event event = service.events().get(id, externalEventId).execute();
            return parseGoogleEvent(event);
        } catch (Exception e) {
            throw new CalendarException("GET_EVENT_FAILED",
                    "Failed to get Google event: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Webhook (events.watch)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public WebhookInfo registerWebhook(CustomerUserSyncEntity sync, String calendarId, String callbackUrl) {
        String id = calendarId != null ? calendarId : PRIMARY;
        log.info("Registering Google webhook for calendar {} -> {}", id, callbackUrl);
        try {
            Calendar service = buildService(sync.getAccessToken());
            String channelId = UUID.randomUUID().toString();
            long expirationMs = System.currentTimeMillis() + 604_800_000L; // 7 days

            Channel channel = new Channel()
                    .setId(channelId)
                    .setType("web_hook")
                    .setAddress(callbackUrl)
                    .setExpiration(expirationMs);

            Channel response = service.events().watch(id, channel).execute();
            OffsetDateTime expiry = OffsetDateTime.ofInstant(
                    Instant.ofEpochMilli(response.getExpiration()), ZoneOffset.UTC);
            log.info("Google webhook registered: channelId={} resourceId={} expiry={}",
                    response.getId(), response.getResourceId(), expiry);
            return new WebhookInfo(response.getId(), response.getResourceId(), expiry);
        } catch (Exception e) {
            log.error("Failed to register Google webhook for calendar {}: {}", id, e.getMessage());
            throw new CalendarException("WEBHOOK_REGISTRATION_FAILED",
                    "Failed to register Google webhook: " + e.getMessage(), e);
        }
    }

    @Override
    public WebhookInfo renewWebhook(CustomerUserSyncEntity sync, String channelId, String calendarId, String callbackUrl) {
        // Google does not support renewal — stop old and register new
        log.info("Renewing Google webhook for calendar {}", calendarId);
        return registerWebhook(sync, calendarId, callbackUrl);
    }

    @Override
    public void deleteWebhook(CustomerUserSyncEntity sync, String channelId, String resourceId) {
        log.info("Stopping Google webhook channel {}", channelId);
        try {
            Calendar service = buildService(sync.getAccessToken());
            Channel channel = new Channel().setId(channelId).setResourceId(resourceId);
            service.channels().stop(channel).execute();
            log.info("Google webhook channel {} stopped", channelId);
        } catch (Exception e) {
            log.warn("Failed to stop Google webhook channel {}: {}", channelId, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getUserEmail / parseEvent
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String getUserEmail(String accessToken) {
        throw new UnsupportedOperationException("Use GoogleOAuthProvider.getUserEmail for profile lookups");
    }

    @Override
    public EventDto parseEvent(Object providerEvent) {
        if (!(providerEvent instanceof Event)) throw new IllegalArgumentException("Expected Google Event");
        return parseGoogleEvent((Event) providerEvent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Calendar buildService(String accessToken) {
        GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
        return new Calendar.Builder(new NetHttpTransport(), JSON_FACTORY, credential)
                .setApplicationName("calendar-service")
                .build();
    }

    private Event toGoogleEvent(EventDto dto) {
        Event event = new Event();
        event.setSummary(dto.getTitle());
        event.setDescription(dto.getDescription());
        event.setLocation(dto.getLocation());

        if (dto.getAttendees() != null) {
            event.setAttendees(dto.getAttendees().stream()
                    .map(a -> new EventAttendee()
                            .setEmail(a.getEmail())
                            .setDisplayName(a.getName()))
                    .collect(Collectors.toList()));
        }

        if (dto.getVisibility() != null) event.setVisibility(dto.getVisibility());
        if (dto.getTransparency() != null) event.setTransparency(dto.getTransparency());

        if (dto.isAllDay()) {
            LocalDate start = Objects.requireNonNullElseGet(dto.getStartTime(), () -> OffsetDateTime.now(ZoneOffset.UTC))
                    .toLocalDate();
            LocalDate end = Objects.requireNonNullElseGet(dto.getEndTime(), () -> OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))
                    .toLocalDate();
            event.setStart(new EventDateTime().setDate(new DateTime(start.toString())));
            event.setEnd(new EventDateTime().setDate(new DateTime(end.toString())));
        } else {
            if (dto.getStartTime() != null)
                event.setStart(new EventDateTime()
                        .setDateTime(new DateTime(dto.getStartTime().toInstant().toEpochMilli()))
                        .setTimeZone("UTC"));
            if (dto.getEndTime() != null)
                event.setEnd(new EventDateTime()
                        .setDateTime(new DateTime(dto.getEndTime().toInstant().toEpochMilli()))
                        .setTimeZone("UTC"));
        }

        return event;
    }

    EventDto parseGoogleEvent(Event event) {
        boolean cancelled = "cancelled".equalsIgnoreCase(event.getStatus());
        OffsetDateTime start = parseDateTime(event.getStart());
        OffsetDateTime end = parseDateTime(event.getEnd());
        boolean allDay = event.getStart() != null && event.getStart().getDate() != null;

        OffsetDateTime updated = null;
        if (event.getUpdated() != null)
            updated = OffsetDateTime.ofInstant(Instant.ofEpochMilli(event.getUpdated().getValue()), ZoneOffset.UTC);

        OffsetDateTime created = null;
        if (event.getCreated() != null)
            created = OffsetDateTime.ofInstant(Instant.ofEpochMilli(event.getCreated().getValue()), ZoneOffset.UTC);

        // Recurrence rules
        List<String> recurrenceRules = event.getRecurrence() != null
                ? new ArrayList<>(event.getRecurrence()) : null;

        // Attendees
        List<AttendeeDto> attendees = null;
        if (event.getAttendees() != null) {
            attendees = event.getAttendees().stream()
                    .map(a -> AttendeeDto.builder()
                            .email(a.getEmail())
                            .name(a.getDisplayName())
                            .status(mapGoogleResponseStatus(a.getResponseStatus()))
                            .build())
                    .collect(Collectors.toList());
        }

        // Reminders
        List<ReminderDto> reminders = null;
        if (event.getReminders() != null && event.getReminders().getOverrides() != null) {
            reminders = event.getReminders().getOverrides().stream()
                    .map(r -> ReminderDto.builder()
                            .method(r.getMethod())
                            .minutes(r.getMinutes())
                            .build())
                    .collect(Collectors.toList());
        }

        // Conference data
        String conferenceData = null;
        if (event.getConferenceData() != null) {
            try {
                conferenceData = new ObjectMapper().writeValueAsString(event.getConferenceData());
            } catch (Exception ignored) {}
        }

        String timeZone = "UTC";
        if (event.getStart() != null && event.getStart().getTimeZone() != null) {
            timeZone = event.getStart().getTimeZone();
        }

        return EventDto.builder()
                .externalId(event.getId())
                .iCalUID(event.getICalUID())
                .recurringEventId(event.getRecurringEventId())
                .title(event.getSummary() != null ? event.getSummary() : "")
                .description(event.getDescription())
                .location(event.getLocation())
                .organizer(event.getOrganizer() != null ? event.getOrganizer().getEmail() : null)
                .startTime(start)
                .endTime(end)
                .timeZoneId(timeZone)
                .allDay(allDay)
                .status(event.getStatus())
                .isCancelled(cancelled)
                .provider(ProviderType.GOOGLE)
                .source(EventSource.GOOGLE)
                .externalUpdatedAt(updated)
                .createdAt(created)
                .attendees(attendees)
                .reminders(reminders)
                .recurrenceRules(recurrenceRules)
                .conferenceData(conferenceData)
                .htmlLink(event.getHtmlLink())
                .eventType(null)
                .sequence(event.getSequence())
                .etag(event.getEtag())
                .transparency(event.getTransparency())
                .visibility(event.getVisibility())
                .build();
    }

    private static OffsetDateTime parseDateTime(EventDateTime edt) {
        if (edt == null) return null;
        if (edt.getDateTime() != null)
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(edt.getDateTime().getValue()), ZoneOffset.UTC);
        if (edt.getDate() != null) {
            LocalDate d = LocalDate.parse(edt.getDate().toStringRfc3339().substring(0, 10));
            return d.atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        return null;
    }

    private static <T> List<T> safe(List<T> in) {
        return in == null ? List.of() : in;
    }

    private static String mapGoogleResponseStatus(String s) {
        if (s == null) return "PENDING";
        return switch (s.toLowerCase()) {
            case "accepted" -> "ACCEPTED";
            case "declined" -> "DECLINED";
            case "tentative" -> "TENTATIVE";
            default -> "PENDING";
        };
    }
}
