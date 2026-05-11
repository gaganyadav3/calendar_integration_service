
package com.omvrti.calendar_service.calendar.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.dto.AttendeeDto;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.CalendarException;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Zoho Calendar provider implementation using Zoho Calendar API.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ZohoCalendarProvider implements ICalendarProvider {

    private static final String CALENDAR_API_BASE = "https://calendar.zoho.com/api/v1";
    private static final String CALENDARS_ENDPOINT = CALENDAR_API_BASE + "/calendars";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ProviderType getProviderType() {
        return ProviderType.ZOHO;
    }

    @Override
    public List<EventDto> fetchEvents(ConnectedAccountEntity account, OffsetDateTime since) {
        try {
            String calendarId = getPrimaryCalendarId(account.getAccessToken());
            String url = UriComponentsBuilder.fromHttpUrl(CALENDARS_ENDPOINT + "/" + calendarId + "/events")
                    .queryParam("startdate", since.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate().toString())
                    .build()
                    .toUriString();

            return fetchEventsFromUrl(url, account.getAccessToken());
        } catch (Exception e) {
            throw new CalendarException("FETCH_EVENTS_FAILED", "Failed to fetch Zoho events: " + e.getMessage(), e);
        }
    }

    @Override
    public List<EventDto> fetchAllEvents(ConnectedAccountEntity account) {
        try {
            String calendarId = getPrimaryCalendarId(account.getAccessToken());
            String url = CALENDARS_ENDPOINT + "/" + calendarId + "/events";
            return fetchEventsFromUrl(url, account.getAccessToken());
        } catch (Exception e) {
            throw new CalendarException("FETCH_EVENTS_FAILED", "Failed to fetch Zoho events: " + e.getMessage(), e);
        }
    }

    @Override
    public String createEvent(ConnectedAccountEntity account, EventDto event) {
        try {
            String calendarId = getPrimaryCalendarId(account.getAccessToken());
            ObjectNode payload = toZohoEvent(event);
            ResponseEntity<String> response = restTemplate.exchange(
                    CALENDARS_ENDPOINT + "/" + calendarId + "/events",
                    HttpMethod.POST,
                    new HttpEntity<>(payload.toString(), headers(account.getAccessToken())),
                    String.class
            );

            if (response.getStatusCode() != HttpStatus.CREATED && response.getStatusCode() != HttpStatus.OK) {
                throw new CalendarException("CREATE_EVENT_FAILED", "Unexpected response: " + response.getStatusCode());
            }

            JsonNode node = objectMapper.readTree(response.getBody());
            return node.get("id").asText();
        } catch (Exception e) {
            throw new CalendarException("CREATE_EVENT_FAILED", "Failed to create Zoho event: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateEvent(ConnectedAccountEntity account, String externalEventId, EventDto event) {
        try {
            String calendarId = getPrimaryCalendarId(account.getAccessToken());
            ObjectNode payload = toZohoEvent(event);
            String url = CALENDARS_ENDPOINT + "/" + calendarId + "/events/" + externalEventId;
            restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(payload.toString(), headers(account.getAccessToken())), String.class);
        } catch (Exception e) {
            throw new CalendarException("UPDATE_EVENT_FAILED", "Failed to update Zoho event: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteEvent(ConnectedAccountEntity account, String externalEventId) {
        try {
            String calendarId = getPrimaryCalendarId(account.getAccessToken());
            String url = CALENDARS_ENDPOINT + "/" + calendarId + "/events/" + externalEventId;
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers(account.getAccessToken())), String.class);
        } catch (Exception e) {
            throw new CalendarException("DELETE_EVENT_FAILED", "Failed to delete Zoho event: " + e.getMessage(), e);
        }
    }

    @Override
    public EventDto getEvent(ConnectedAccountEntity account, String externalEventId) {
        try {
            String calendarId = getPrimaryCalendarId(account.getAccessToken());
            String url = CALENDARS_ENDPOINT + "/" + calendarId + "/events/" + externalEventId;
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(account.getAccessToken())), String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new CalendarException("GET_EVENT_FAILED", "Unexpected response: " + response.getStatusCode());
            }
            JsonNode node = objectMapper.readTree(response.getBody());
            return parseZohoEvent(node);
        } catch (Exception e) {
            throw new CalendarException("GET_EVENT_FAILED", "Failed to get Zoho event: " + e.getMessage(), e);
        }
    }

    @Override
    public String getUserEmail(String accessToken) {
        // Use OAuth provider
        throw new UnsupportedOperationException("Use ZohoOAuthProvider#getUserEmail for profile lookups");
    }

    @Override
    public EventDto parseEvent(Object providerEvent) {
        if (!(providerEvent instanceof JsonNode)) {
            throw new IllegalArgumentException("Expected JsonNode");
        }
        return parseZohoEvent((JsonNode) providerEvent);
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

    private List<EventDto> fetchEventsFromUrl(String url, String accessToken) throws Exception {
        List<EventDto> out = new ArrayList<>();
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(accessToken)), String.class);
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new CalendarException("FETCH_EVENTS_FAILED", "Unexpected response: " + response.getStatusCode());
        }
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode events = root.get("events");
        if (events != null && events.isArray()) {
            for (JsonNode n : events) {
                out.add(parseZohoEvent(n));
            }
        }
        return out;
    }

    private EventDto parseZohoEvent(JsonNode node) {
        boolean cancelled = node.has("is_cancelled") && node.get("is_cancelled").asBoolean(false);
        boolean allDay = node.has("is_allday") && node.get("is_allday").asBoolean(false);

        OffsetDateTime start = null;
        OffsetDateTime end = null;
        if (node.has("start")) {
            start = OffsetDateTime.parse(node.get("start").get("datetime").asText()).withOffsetSameInstant(ZoneOffset.UTC);
        }
        if (node.has("end")) {
            end = OffsetDateTime.parse(node.get("end").get("datetime").asText()).withOffsetSameInstant(ZoneOffset.UTC);
        }

        OffsetDateTime updated = null;
        if (node.has("last_modified_time")) {
            updated = OffsetDateTime.parse(node.get("last_modified_time").asText()).withOffsetSameInstant(ZoneOffset.UTC);
        }

        String organizer = node.path("organizer").path("email").asText(null);
        String location = node.path("location").asText(null);

        List<AttendeeDto> attendees = new ArrayList<>();
        if (node.has("attendees") && node.get("attendees").isArray()) {
            for (JsonNode attendeeNode : node.get("attendees")) {
                String email = attendeeNode.path("email").asText(null);
                String name = attendeeNode.path("name").asText(null);
                String status = mapZohoStatus(attendeeNode.path("status").asText("pending"));
                attendees.add(AttendeeDto.builder().email(email).name(name).status(status).build());
            }
        }

        return EventDto.builder()
                .externalId(node.get("id").asText())
                .title(node.has("title") ? node.get("title").asText("") : "")
                .description(node.has("description") ? node.get("description").asText(null) : null)
                .location(location)
                .organizer(organizer)
                .startTime(start)
                .endTime(end)
                .timeZoneId("UTC")
                .allDay(allDay)
                .status(cancelled ? "CANCELLED" : "CONFIRMED")
                .isCancelled(cancelled)
                .provider(ProviderType.ZOHO)
                .source(EventSource.ZOHO)
                .externalUpdatedAt(updated)
                .attendees(attendees)
                .build();
    }

    private ObjectNode toZohoEvent(EventDto event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", event.getTitle());
        if (event.getDescription() != null) {
            node.put("description", event.getDescription());
        }
        if (event.getLocation() != null) {
            node.put("location", event.getLocation());
        }

        node.put("is_allday", event.isAllDay());
        if (event.getStartTime() != null) {
            ObjectNode start = objectMapper.createObjectNode();
            start.put("datetime", event.getStartTime().withOffsetSameInstant(ZoneOffset.UTC).toString());
            start.put("timezone", "UTC");
            node.set("start", start);
        }
        if (event.getEndTime() != null) {
            ObjectNode end = objectMapper.createObjectNode();
            end.put("datetime", event.getEndTime().withOffsetSameInstant(ZoneOffset.UTC).toString());
            end.put("timezone", "UTC");
            node.set("end", end);
        }
        if (event.getAttendees() != null && !event.getAttendees().isEmpty()) {
            com.fasterxml.jackson.databind.node.ArrayNode attendeesNode = node.putArray("attendees");
            for (AttendeeDto attendee : event.getAttendees()) {
                ObjectNode attendeeNode = attendeesNode.addObject();
                attendeeNode.put("email", attendee.getEmail());
                attendeeNode.put("name", attendee.getName());
            }
        }
        return node;
    }

    private static HttpHeaders headers(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private String getPrimaryCalendarId(String accessToken) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(CALENDARS_ENDPOINT, HttpMethod.GET, new HttpEntity<>(headers(accessToken)), String.class);
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new CalendarException("CALENDAR_FETCH_FAILED", "Failed to fetch calendars");
        }
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode calendars = root.get("calendars");
        if (calendars != null && calendars.isArray() && calendars.size() > 0) {
            return calendars.get(0).get("id").asText();
        }
        throw new CalendarException("CALENDAR_FETCH_FAILED", "No calendars found");
    }

    private static String mapZohoStatus(String status) {
        if (status == null) return "PENDING";
        switch (status.toLowerCase()) {
            case "accepted": return "ACCEPTED";
            case "declined": return "DECLINED";
            case "tentative": return "TENTATIVE";
            case "pending": return "PENDING";
            default: return "PENDING";
        }
    }
}
