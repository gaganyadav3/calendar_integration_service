package com.omvrti.calendar_service.calendar.provider.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.dto.AttendeeDto;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.CalendarException;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Google Calendar provider implementation using Google Calendar API.
 *
 * Notes:
 * - Tokens are expected to be refreshed by TokenRefreshService before calls.
 * - All times are normalized to UTC in EventDto.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarProvider implements ICalendarProvider {

    private static final String CALENDAR_ID = "primary";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    @Override
    public ProviderType getProviderType() {
        return ProviderType.GOOGLE;
    }

    @Override
    public List<EventDto> fetchEvents(ConnectedAccountEntity account, OffsetDateTime since) {
        return fetchEventsWithToken(account, null).events();
    }

    @Override
    public SyncFetchResult fetchEventsWithToken(ConnectedAccountEntity account, String syncToken) {
        try {
            Calendar calendar = buildCalendarService(account.getAccessToken());
            List<EventDto> allEvents = new ArrayList<>();
            String pageToken = null;
            String nextSyncToken = null;

            do {
                Calendar.Events.List request = calendar.events()
                        .list(CALENDAR_ID)
                        .setSingleEvents(true)
                        .setMaxResults(2500)
                        .setShowDeleted(true);

                if (syncToken != null) {
                    request.setSyncToken(syncToken);
                }
                if (pageToken != null) {
                    request.setPageToken(pageToken);
                }

                Events result = request.execute();

                for (Event event : safe(result.getItems())) {
                    allEvents.add(parseGoogleEvent(event));
                }

                pageToken = result.getNextPageToken();
                if (pageToken == null) {
                    nextSyncToken = result.getNextSyncToken();
                }
            } while (pageToken != null);

            return new SyncFetchResult(allEvents, nextSyncToken);

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 410) {
                throw new CalendarException("SYNC_TOKEN_EXPIRED",
                        "Google sync token expired (410 Gone) — full resync required", e);
            }
            throw new CalendarException("FETCH_EVENTS_FAILED", "Failed to fetch Google events: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new CalendarException("FETCH_EVENTS_FAILED", "Failed to fetch Google events: " + e.getMessage(), e);
        }
    }

    @Override
    public List<EventDto> fetchAllEvents(ConnectedAccountEntity account) {
        try {
            Calendar calendar = buildCalendarService(account.getAccessToken());
            Events result = calendar.events()
                    .list(CALENDAR_ID)
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setMaxResults(2500)
                    .setShowDeleted(true)
                    .execute();

            List<EventDto> out = new ArrayList<>();
            for (Event event : safe(result.getItems())) {
                out.add(parseGoogleEvent(event));
            }
            return out;
        } catch (Exception e) {
            throw new CalendarException("FETCH_EVENTS_FAILED", "Failed to fetch Google events: " + e.getMessage(), e);
        }
    }

    @Override
    public String createEvent(ConnectedAccountEntity account, EventDto event) {
        try {
            Calendar calendar = buildCalendarService(account.getAccessToken());
            Event googleEvent = toGoogleEvent(event);
            Event created = calendar.events().insert(CALENDAR_ID, googleEvent).execute();
            return created.getId();
        } catch (Exception e) {
            throw new CalendarException("CREATE_EVENT_FAILED", "Failed to create Google event: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateEvent(ConnectedAccountEntity account, String externalEventId, EventDto event) {
        try {
            Calendar calendar = buildCalendarService(account.getAccessToken());
            Event googleEvent = toGoogleEvent(event);
            calendar.events().update(CALENDAR_ID, externalEventId, googleEvent).execute();
        } catch (Exception e) {
            throw new CalendarException("UPDATE_EVENT_FAILED", "Failed to update Google event: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteEvent(ConnectedAccountEntity account, String externalEventId) {
        try {
            Calendar calendar = buildCalendarService(account.getAccessToken());
            calendar.events().delete(CALENDAR_ID, externalEventId).execute();
        } catch (Exception e) {
            throw new CalendarException("DELETE_EVENT_FAILED", "Failed to delete Google event: " + e.getMessage(), e);
        }
    }

    @Override
    public EventDto getEvent(ConnectedAccountEntity account, String externalEventId) {
        try {
            Calendar calendar = buildCalendarService(account.getAccessToken());
            Event event = calendar.events().get(CALENDAR_ID, externalEventId).execute();
            return parseGoogleEvent(event);
        } catch (Exception e) {
            throw new CalendarException("GET_EVENT_FAILED", "Failed to get Google event: " + e.getMessage(), e);
        }
    }

    @Override
    public String getUserEmail(String accessToken) {
        // Prefer using the OAuth provider's userinfo call.
        throw new UnsupportedOperationException("Use GoogleOAuthProvider#getUserEmail for profile lookups");
    }

    @Override
    public EventDto parseEvent(Object providerEvent) {
        if (!(providerEvent instanceof Event)) {
            throw new IllegalArgumentException("Expected Google Event");
        }
        return parseGoogleEvent((Event) providerEvent);
    }

    @Override
    public CalendarStateSummary getCurrentStateSummary(ConnectedAccountEntity account) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<EventDto> all = fetchAllEvents(account);
        long totalEvents = all.stream().filter(e -> !e.isCancelled()).count();
        long totalBookings = all.stream()
                .filter(e -> !e.isCancelled())
                .filter(e -> e.getEndTime() != null && e.getEndTime().isAfter(now))
                .count();
        return new CalendarStateSummary(totalEvents, totalBookings, now);
    }

    private Calendar buildCalendarService(String accessToken) {
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

        if (dto.getAttendees() != null && !dto.getAttendees().isEmpty()) {
            List<EventAttendee> attendees = dto.getAttendees().stream()
                    .map(a -> new EventAttendee().setEmail(a.getEmail()).setDisplayName(a.getName()))
                    .collect(Collectors.toList());
            event.setAttendees(attendees);
        }

        if (dto.isAllDay()) {
            LocalDate start = Objects.requireNonNullElseGet(dto.getStartTime(), () -> OffsetDateTime.now(ZoneOffset.UTC))
                    .toLocalDate();
            LocalDate end = Objects.requireNonNullElseGet(dto.getEndTime(), () -> OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))
                    .toLocalDate();

            event.setStart(new EventDateTime().setDate(new DateTime(start.toString())));
            event.setEnd(new EventDateTime().setDate(new DateTime(end.toString())));
        } else {
            if (dto.getStartTime() != null) {
                event.setStart(new EventDateTime()
                        .setDateTime(new DateTime(dto.getStartTime().toInstant().toEpochMilli()))
                        .setTimeZone("UTC"));
            }
            if (dto.getEndTime() != null) {
                event.setEnd(new EventDateTime()
                        .setDateTime(new DateTime(dto.getEndTime().toInstant().toEpochMilli()))
                        .setTimeZone("UTC"));
            }
        }

        return event;
    }

    private EventDto parseGoogleEvent(Event event) {
        boolean cancelled = "cancelled".equalsIgnoreCase(event.getStatus());

        OffsetDateTime start = parseGoogleDateTime(event.getStart());
        OffsetDateTime end = parseGoogleDateTime(event.getEnd());

        OffsetDateTime updated = null;
        if (event.getUpdated() != null) {
            updated = OffsetDateTime.ofInstant(Instant.ofEpochMilli(event.getUpdated().getValue()), ZoneOffset.UTC);
        }

        boolean allDay = event.getStart() != null && event.getStart().getDate() != null;

        return EventDto.builder()
                .externalId(event.getId())
                .title(event.getSummary() != null ? event.getSummary() : "")
                .description(event.getDescription())
                .location(event.getLocation())
                .organizer(event.getOrganizer() != null ? event.getOrganizer().getEmail() : null)
                .startTime(start)
                .endTime(end)
                .timeZoneId("UTC")
                .allDay(allDay)
                .status(event.getStatus())
                .isCancelled(cancelled)
                .provider(ProviderType.GOOGLE)
                .source(EventSource.GOOGLE)
                .externalUpdatedAt(updated)
                .attendees(event.getAttendees() != null ? event.getAttendees().stream()
                        .map(a -> AttendeeDto.builder()
                                .email(a.getEmail())
                                .name(a.getDisplayName())
                                .status(mapGoogleResponseStatus(a.getResponseStatus()))
                                .build())
                        .collect(Collectors.toList()) : null)
                .build();
    }

    private static OffsetDateTime parseGoogleDateTime(EventDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        if (dateTime.getDateTime() != null) {
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(dateTime.getDateTime().getValue()), ZoneOffset.UTC);
        }
        if (dateTime.getDate() != null) {
            LocalDate d = LocalDate.parse(dateTime.getDate().toStringRfc3339().substring(0, 10));
            return d.atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        return null;
    }

    private static <T> List<T> safe(List<T> in) {
        return in == null ? List.of() : in;
    }

    private static String mapGoogleResponseStatus(String responseStatus) {
        if (responseStatus == null) return "PENDING";
        switch (responseStatus.toLowerCase()) {
            case "accepted": return "ACCEPTED";
            case "declined": return "DECLINED";
            case "tentative": return "TENTATIVE";
            case "needsaction": return "PENDING";
            default: return "PENDING";
        }
    }
}
