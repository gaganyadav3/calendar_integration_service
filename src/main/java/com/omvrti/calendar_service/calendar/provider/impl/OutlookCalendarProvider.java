package com.omvrti.calendar_service.calendar.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.stream.Collectors;

/**
 * Outlook (Microsoft Graph) calendar provider.
 *
 * Notes:
 * - Tokens are expected to be refreshed by TokenRefreshService before calls.
 * - Stores/returns times in UTC in EventDto.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutlookCalendarProvider implements ICalendarProvider {

    private static final String GRAPH_API_BASE = "https://graph.microsoft.com/v1.0";
    private static final String EVENTS_ENDPOINT = GRAPH_API_BASE + "/me/events";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ProviderType getProviderType() {
        return ProviderType.OUTLOOK;
    }

    @Override
    public List<EventDto> fetchEvents(ConnectedAccountEntity account, OffsetDateTime since) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(EVENTS_ENDPOINT)
                    .queryParam("$top", 100)
                    .queryParam("$orderby", "lastModifiedDateTime")
                    .queryParam("$select", "id,subject,bodyPreview,location,organizer,start,end,isAllDay,isCancelled,lastModifiedDateTime,attendees")
                    .queryParam("$filter", "lastModifiedDateTime ge " + since.withOffsetSameInstant(ZoneOffset.UTC))
                    .build()
                    .toUriString();

            return fetchPaged(url, account.getAccessToken());
        } catch (Exception e) {
            throw new CalendarException("FETCH_EVENTS_FAILED", "Failed to fetch Outlook events: " + e.getMessage(), e);
        }
    }

    @Override
    public List<EventDto> fetchAllEvents(ConnectedAccountEntity account) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(EVENTS_ENDPOINT)
                    .queryParam("$top", 100)
                    .queryParam("$orderby", "start/dateTime")
                    .queryParam("$select", "id,subject,bodyPreview,location,organizer,start,end,isAllDay,isCancelled,lastModifiedDateTime,attendees")
                    .build()
                    .toUriString();
            return fetchPaged(url, account.getAccessToken());
        } catch (Exception e) {
            throw new CalendarException("FETCH_EVENTS_FAILED", "Failed to fetch Outlook events: " + e.getMessage(), e);
        }
    }

    @Override
    public String createEvent(ConnectedAccountEntity account, EventDto event) {
        try {
            ObjectNode payload = toGraphEvent(event);
            ResponseEntity<String> response = restTemplate.exchange(
                    EVENTS_ENDPOINT,
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
            throw new CalendarException("CREATE_EVENT_FAILED", "Failed to create Outlook event: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateEvent(ConnectedAccountEntity account, String externalEventId, EventDto event) {
        try {
            ObjectNode payload = toGraphEvent(event);
            String url = EVENTS_ENDPOINT + "/" + externalEventId;
            restTemplate.exchange(url, HttpMethod.PATCH, new HttpEntity<>(payload.toString(), headers(account.getAccessToken())), String.class);
        } catch (Exception e) {
            throw new CalendarException("UPDATE_EVENT_FAILED", "Failed to update Outlook event: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteEvent(ConnectedAccountEntity account, String externalEventId) {
        try {
            String url = EVENTS_ENDPOINT + "/" + externalEventId;
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers(account.getAccessToken())), String.class);
        } catch (Exception e) {
            throw new CalendarException("DELETE_EVENT_FAILED", "Failed to delete Outlook event: " + e.getMessage(), e);
        }
    }

    @Override
    public EventDto getEvent(ConnectedAccountEntity account, String externalEventId) {
        try {
            String url = EVENTS_ENDPOINT + "/" + externalEventId + "?$select=id,subject,bodyPreview,location,organizer,start,end,isAllDay,isCancelled,lastModifiedDateTime,attendees";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(account.getAccessToken())), String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new CalendarException("GET_EVENT_FAILED", "Unexpected response: " + response.getStatusCode());
            }
            JsonNode node = objectMapper.readTree(response.getBody());
            return parseGraphEvent(node);
        } catch (Exception e) {
            throw new CalendarException("GET_EVENT_FAILED", "Failed to get Outlook event: " + e.getMessage(), e);
        }
    }

    @Override
    public String getUserEmail(String accessToken) {
        // Prefer using the OAuth provider's /me call.
        throw new UnsupportedOperationException("Use OutlookOAuthProvider#getUserEmail for profile lookups");
    }

    @Override
    public EventDto parseEvent(Object providerEvent) {
        if (!(providerEvent instanceof JsonNode)) {
            throw new IllegalArgumentException("Expected JsonNode");
        }
        return parseGraphEvent((JsonNode) providerEvent);
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

    private List<EventDto> fetchPaged(String firstUrl, String accessToken) throws Exception {
        List<EventDto> out = new ArrayList<>();
        String next = firstUrl;
        while (next != null) {
            ResponseEntity<String> response = restTemplate.exchange(next, HttpMethod.GET, new HttpEntity<>(headers(accessToken)), String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new CalendarException("FETCH_EVENTS_FAILED", "Unexpected response: " + response.getStatusCode());
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode values = root.get("value");
            if (values != null && values.isArray()) {
                for (JsonNode n : values) {
                    out.add(parseGraphEvent(n));
                }
            }
            next = root.has("@odata.nextLink") ? root.get("@odata.nextLink").asText() : null;
        }
        return out;
    }

    private EventDto parseGraphEvent(JsonNode node) {
        boolean cancelled = node.has("isCancelled") && node.get("isCancelled").asBoolean(false);
        boolean allDay = node.has("isAllDay") && node.get("isAllDay").asBoolean(false);

        OffsetDateTime start = null;
        OffsetDateTime end = null;
        if (node.has("start") && node.get("start").has("dateTime")) {
            start = parseGraphDateTime(node.get("start").get("dateTime").asText(), node.get("start").path("timeZone").asText("UTC"));
        }
        if (node.has("end") && node.get("end").has("dateTime")) {
            end = parseGraphDateTime(node.get("end").get("dateTime").asText(), node.get("end").path("timeZone").asText("UTC"));
        }

        OffsetDateTime updated = null;
        if (node.has("lastModifiedDateTime")) {
            updated = parseGraphDateTime(node.get("lastModifiedDateTime").asText(), "UTC");
        }

        String organizer = null;
        if (node.has("organizer") && node.get("organizer").has("emailAddress")) {
            organizer = node.get("organizer").get("emailAddress").get("address").asText(null);
        }

        String location = null;
        if (node.has("location") && node.get("location").has("displayName")) {
            location = node.get("location").get("displayName").asText(null);
        }

        List<AttendeeDto> attendees = new ArrayList<>();
        if (node.has("attendees") && node.get("attendees").isArray()) {
            for (JsonNode attendeeNode : node.get("attendees")) {
                String email = attendeeNode.path("emailAddress").path("address").asText(null);
                String name = attendeeNode.path("emailAddress").path("name").asText(null);
                String response = attendeeNode.path("status").path("response").asText("notResponded");
                String status = mapOutlookResponse(response);
                attendees.add(AttendeeDto.builder().email(email).name(name).status(status).build());
            }
        }

        return EventDto.builder()
                .externalId(node.get("id").asText())
                .title(node.has("subject") ? node.get("subject").asText("") : "")
                .description(node.has("bodyPreview") ? node.get("bodyPreview").asText(null) : null)
                .location(location)
                .organizer(organizer)
                .startTime(start)
                .endTime(end)
                .timeZoneId("UTC")
                .allDay(allDay)
                .status(cancelled ? "CANCELLED" : "CONFIRMED")
                .isCancelled(cancelled)
                .provider(ProviderType.OUTLOOK)
                .source(EventSource.OUTLOOK)
                .externalUpdatedAt(updated)
                .attendees(attendees)
                .build();
    }

    private ObjectNode toGraphEvent(EventDto event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("subject", event.getTitle());
        if (event.getDescription() != null) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("contentType", "text");
            body.put("content", event.getDescription());
            node.set("body", body);
        }
        if (event.getLocation() != null) {
            ObjectNode location = objectMapper.createObjectNode();
            location.put("displayName", event.getLocation());
            node.set("location", location);
        }

        node.put("isAllDay", event.isAllDay());
        if (event.getStartTime() != null) {
            ObjectNode start = objectMapper.createObjectNode();
            start.put("dateTime", event.getStartTime().withOffsetSameInstant(ZoneOffset.UTC).toString());
            start.put("timeZone", "UTC");
            node.set("start", start);
        }
        if (event.getEndTime() != null) {
            ObjectNode end = objectMapper.createObjectNode();
            end.put("dateTime", event.getEndTime().withOffsetSameInstant(ZoneOffset.UTC).toString());
            end.put("timeZone", "UTC");
            node.set("end", end);
        }
        if (event.getAttendees() != null && !event.getAttendees().isEmpty()) {
            ArrayNode attendeesNode = node.putArray("attendees");
            for (AttendeeDto attendee : event.getAttendees()) {
                ObjectNode attendeeNode = attendeesNode.addObject();
                ObjectNode emailNode = attendeeNode.putObject("emailAddress");
                emailNode.put("address", attendee.getEmail());
                emailNode.put("name", attendee.getName());
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

    private static OffsetDateTime parseGraphDateTime(String dateTime, String timeZone) {
        if (dateTime == null || dateTime.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(dateTime).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // Graph often returns local "YYYY-MM-DDTHH:mm:ss(.SSS...)" + a separate timeZone field.
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dateTime);
            java.time.ZoneId zoneId;
            try {
                zoneId = java.time.ZoneId.of(timeZone);
            } catch (Exception e) {
                zoneId = ZoneOffset.UTC;
            }
            return ldt.atZone(zoneId).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        }
    }

    private static String mapOutlookResponse(String response) {
        if (response == null) return "PENDING";
        switch (response.toLowerCase()) {
            case "accepted": return "ACCEPTED";
            case "declined": return "DECLINED";
            case "tentativelyaccepted": return "TENTATIVE";
            case "notresponded": return "PENDING";
            default: return "PENDING";
        }
    }
}
