package com.omvrti.calendar_service.calendar.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.common.dto.AttendeeDto;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.CalendarException;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Zoho Calendar provider implementation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ZohoCalendarProvider implements ICalendarProvider {

    private static final String BASE = "https://calendar.zoho.com/api/v1";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ProviderType getProviderType() {
        return ProviderType.ZOHO;
    }

    @Override
    public List<CalendarInfo> fetchCalendars(CustomerUserSyncEntity sync) {
        try {
            ResponseEntity<String> resp = get(BASE + "/calendars", sync.getAccessToken());
            JsonNode root = objectMapper.readTree(resp.getBody());
            List<CalendarInfo> result = new ArrayList<>();
            JsonNode cals = root.get("calendars");
            if (cals != null && cals.isArray()) {
                for (JsonNode c : cals) {
                    result.add(new CalendarInfo(
                            c.get("id").asText(), c.path("name").asText("Calendar"),
                            null, null, c.path("isprimary").asBoolean(false), true));
                }
            }
            return result;
        } catch (Exception e) {
            throw new CalendarException("FETCH_CALENDARS_FAILED",
                    "Failed to fetch Zoho calendars: " + e.getMessage(), e);
        }
    }

    @Override
    public List<EventDto> fetchEvents(CustomerUserSyncEntity sync, String calendarId, OffsetDateTime since) {
        try {
            String url = BASE + "/calendars/" + calendarId + "/events"
                    + (since != null ? "?startdate=" + since.toLocalDate() : "");
            return fetchFromUrl(url, sync.getAccessToken());
        } catch (Exception e) {
            throw new CalendarException("FETCH_EVENTS_FAILED", "Failed to fetch Zoho events: " + e.getMessage(), e);
        }
    }

    @Override
    public List<EventDto> fetchAllEvents(CustomerUserSyncEntity sync, String calendarId) {
        try {
            return fetchFromUrl(BASE + "/calendars/" + calendarId + "/events", sync.getAccessToken());
        } catch (Exception e) {
            throw new CalendarException("FETCH_EVENTS_FAILED", "Failed to fetch Zoho events: " + e.getMessage(), e);
        }
    }

    @Override
    public String createEvent(CustomerUserSyncEntity sync, String calendarId, EventDto event) {
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    BASE + "/calendars/" + calendarId + "/events", HttpMethod.POST,
                    entity(toZoho(event).toString(), sync.getAccessToken()), String.class);
            return objectMapper.readTree(resp.getBody()).get("id").asText();
        } catch (Exception e) {
            throw new CalendarException("CREATE_EVENT_FAILED", "Failed to create Zoho event: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateEvent(CustomerUserSyncEntity sync, String calendarId, String externalEventId, EventDto event) {
        try {
            restTemplate.exchange(BASE + "/calendars/" + calendarId + "/events/" + externalEventId,
                    HttpMethod.PUT, entity(toZoho(event).toString(), sync.getAccessToken()), String.class);
        } catch (Exception e) {
            throw new CalendarException("UPDATE_EVENT_FAILED", "Failed to update Zoho event: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteEvent(CustomerUserSyncEntity sync, String calendarId, String externalEventId) {
        try {
            restTemplate.exchange(BASE + "/calendars/" + calendarId + "/events/" + externalEventId,
                    HttpMethod.DELETE, new HttpEntity<>(headers(sync.getAccessToken())), String.class);
        } catch (Exception e) {
            throw new CalendarException("DELETE_EVENT_FAILED", "Failed to delete Zoho event: " + e.getMessage(), e);
        }
    }

    @Override
    public EventDto getEvent(CustomerUserSyncEntity sync, String calendarId, String externalEventId) {
        try {
            ResponseEntity<String> resp = get(
                    BASE + "/calendars/" + calendarId + "/events/" + externalEventId, sync.getAccessToken());
            return parseZohoEvent(objectMapper.readTree(resp.getBody()));
        } catch (Exception e) {
            throw new CalendarException("GET_EVENT_FAILED", "Failed to get Zoho event: " + e.getMessage(), e);
        }
    }

    @Override
    public String getUserEmail(String accessToken) {
        throw new UnsupportedOperationException("Use ZohoOAuthProvider.getUserEmail");
    }

    @Override
    public EventDto parseEvent(Object providerEvent) {
        if (!(providerEvent instanceof JsonNode)) throw new IllegalArgumentException("Expected JsonNode");
        return parseZohoEvent((JsonNode) providerEvent);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<EventDto> fetchFromUrl(String url, String accessToken) throws Exception {
        ResponseEntity<String> resp = get(url, accessToken);
        JsonNode root = objectMapper.readTree(resp.getBody());
        List<EventDto> out = new ArrayList<>();
        JsonNode events = root.get("events");
        if (events != null && events.isArray()) for (JsonNode n : events) out.add(parseZohoEvent(n));
        return out;
    }

    private EventDto parseZohoEvent(JsonNode n) {
        boolean cancelled = n.path("is_cancelled").asBoolean(false);
        OffsetDateTime start = null, end = null;
        if (n.has("start")) try { start = OffsetDateTime.parse(n.get("start").get("datetime").asText()).withOffsetSameInstant(ZoneOffset.UTC); } catch (Exception ignored) {}
        if (n.has("end")) try { end = OffsetDateTime.parse(n.get("end").get("datetime").asText()).withOffsetSameInstant(ZoneOffset.UTC); } catch (Exception ignored) {}
        OffsetDateTime updated = null;
        try { updated = OffsetDateTime.parse(n.path("last_modified_time").asText()).withOffsetSameInstant(ZoneOffset.UTC); } catch (Exception ignored) {}

        List<AttendeeDto> attendees = new ArrayList<>();
        for (JsonNode a : n.path("attendees")) {
            attendees.add(AttendeeDto.builder()
                    .email(a.path("email").asText(null)).name(a.path("name").asText(null))
                    .status(mapZoho(a.path("status").asText("pending"))).build());
        }

        return EventDto.builder()
                .externalId(n.get("id").asText())
                .title(n.path("title").asText(""))
                .description(n.path("description").asText(null))
                .location(n.path("location").asText(null))
                .organizer(n.path("organizer").path("email").asText(null))
                .startTime(start).endTime(end).timeZoneId("UTC")
                .allDay(n.path("is_allday").asBoolean(false))
                .status(cancelled ? "CANCELLED" : "CONFIRMED").isCancelled(cancelled)
                .provider(ProviderType.ZOHO).source(EventSource.ZOHO)
                .externalUpdatedAt(updated).attendees(attendees).build();
    }

    private ObjectNode toZoho(EventDto e) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("title", e.getTitle());
        if (e.getDescription() != null) n.put("description", e.getDescription());
        if (e.getLocation() != null) n.put("location", e.getLocation());
        n.put("is_allday", e.isAllDay());
        if (e.getStartTime() != null) n.putObject("start").put("datetime", e.getStartTime().withOffsetSameInstant(ZoneOffset.UTC).toString());
        if (e.getEndTime() != null) n.putObject("end").put("datetime", e.getEndTime().withOffsetSameInstant(ZoneOffset.UTC).toString());
        return n;
    }

    private ResponseEntity<String> get(String url, String token) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(token)), String.class);
    }

    private HttpEntity<String> entity(String body, String token) {
        return new HttpEntity<>(body, headers(token));
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        h.setBearerAuth(token);
        return h;
    }

    private static String mapZoho(String s) {
        if (s == null) return "PENDING";
        return switch (s.toLowerCase()) {
            case "accepted" -> "ACCEPTED";
            case "declined" -> "DECLINED";
            case "tentative" -> "TENTATIVE";
            default -> "PENDING";
        };
    }
}
