package com.omvrti.calendar_service.calendar.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.*;
import java.util.*;

/**
 * Outlook (Microsoft Graph) calendar provider.
 * Uses delta-link based incremental sync for efficient change tracking.
 * Tokens are refreshed by TokenRefreshService before every call.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutlookCalendarProvider implements ICalendarProvider {

    private static final String GRAPH = "https://graph.microsoft.com/v1.0";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${webhook.callback.base-url:}")
    private String webhookBaseUrl;

    // ─────────────────────────────────────────────────────────────────────────
    // Core identity
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ProviderType getProviderType() {
        return ProviderType.OUTLOOK;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Calendar list
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<CalendarInfo> fetchCalendars(CustomerUserSyncEntity sync) {
        log.debug("Fetching Outlook calendars for {}", sync.getSyncEmail());
        try {
            String url = GRAPH + "/me/calendars?$select=id,name,isDefaultCalendar,canEdit,color,hexColor";
            ResponseEntity<String> resp = get(url, sync.getAccessToken());
            JsonNode root = objectMapper.readTree(resp.getBody());
            List<CalendarInfo> result = new ArrayList<>();
            for (JsonNode n : values(root)) {
                result.add(new CalendarInfo(
                        n.get("id").asText(),
                        n.path("name").asText("Calendar"),
                        n.path("hexColor").asText(null),
                        null,
                        n.path("isDefaultCalendar").asBoolean(false),
                        n.path("canEdit").asBoolean(false)
                ));
            }
            log.info("Fetched {} Outlook calendars for {}", result.size(), sync.getSyncEmail());
            return result;
        } catch (Exception e) {
            throw new CalendarException("FETCH_CALENDARS_FAILED",
                    "Failed to fetch Outlook calendars: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event fetching (time-based + delta)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<EventDto> fetchEvents(CustomerUserSyncEntity sync, String calendarId, OffsetDateTime since) {
        try {
            String effectiveId = resolveCalendarId(calendarId);
            String resource = effectiveId != null
                    ? "/me/calendars/" + effectiveId + "/events"
                    : "/me/events";
            String filter = since != null
                    ? "?$filter=lastModifiedDateTime+ge+" + since.withOffsetSameInstant(ZoneOffset.UTC)
                    + "&$select=id,subject,bodyPreview,body,location,organizer,start,end,isAllDay,isCancelled"
                    + ",lastModifiedDateTime,createdDateTime,attendees,recurrence,onlineMeeting,webLink"
                    + ",importance,sensitivity,responseStatus,categories&$top=100"
                    : "?$select=id,subject,bodyPreview,body,location,organizer,start,end,isAllDay,isCancelled"
                    + ",lastModifiedDateTime,createdDateTime,attendees,recurrence,onlineMeeting,webLink"
                    + ",importance,sensitivity,responseStatus,categories&$top=100";
            return fetchPaged(GRAPH + resource + filter, sync.getAccessToken());
        } catch (Exception e) {
            throw new CalendarException("FETCH_EVENTS_FAILED",
                    "Failed to fetch Outlook events: " + e.getMessage(), e);
        }
    }

    @Override
    public List<EventDto> fetchAllEvents(CustomerUserSyncEntity sync, String calendarId) {
        return fetchEvents(sync, calendarId, null);
    }

    /**
     * Delta-link based incremental sync. The "syncToken" here is the full delta-link URL.
     * On first call pass null; subsequent calls pass the delta link returned by the previous result.
     */
    @Override
    public SyncFetchResult fetchEventsWithToken(CustomerUserSyncEntity sync, String calendarId, String syncToken) {
        log.debug("Outlook delta sync for calendar {} deltaLink={}", calendarId,
                syncToken != null ? "present" : "null");
        try {
            String effectiveId = resolveCalendarId(calendarId);
            String startUrl;
            if (syncToken != null) {
                startUrl = syncToken; // deltaLink from previous run
            } else {
                String resource = effectiveId != null
                        ? "/me/calendars/" + effectiveId + "/events/delta"
                        : "/me/events/delta";
                startUrl = GRAPH + resource
                        + "?$select=id,subject,bodyPreview,body,location,organizer,start,end,isAllDay,isCancelled"
                        + ",lastModifiedDateTime,createdDateTime,attendees,recurrence,onlineMeeting,webLink"
                        + ",importance,sensitivity,responseStatus&$top=100";
            }

            List<EventDto> all = new ArrayList<>();
            String nextDeltaLink = null;
            String next = startUrl;

            while (next != null) {
                ResponseEntity<String> resp = get(next, sync.getAccessToken());
                JsonNode root = objectMapper.readTree(resp.getBody());
                for (JsonNode n : values(root)) all.add(parseGraphEvent(n));
                if (root.has("@odata.deltaLink")) {
                    nextDeltaLink = root.get("@odata.deltaLink").asText();
                    break;
                }
                next = root.has("@odata.nextLink") ? root.get("@odata.nextLink").asText() : null;
            }

            log.info("Outlook delta sync: {} events, deltaLink={}", all.size(),
                    nextDeltaLink != null ? "present" : "none");
            return new SyncFetchResult(all, nextDeltaLink);

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.GONE) {
                throw new CalendarException("SYNC_TOKEN_EXPIRED",
                        "Outlook delta link expired (410 Gone) — full resync required", e);
            }
            throw new CalendarException("FETCH_EVENTS_FAILED",
                    "Outlook Graph API error: " + e.getMessage(), e);
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            throw new CalendarException("FETCH_EVENTS_FAILED",
                    "Failed to fetch Outlook events: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String createEvent(CustomerUserSyncEntity sync, String calendarId, EventDto event) {
        String effectiveId = resolveCalendarId(calendarId);
        String url = effectiveId != null
                ? GRAPH + "/me/calendars/" + effectiveId + "/events"
                : GRAPH + "/me/events";
        try {
            ObjectNode payload = toGraphEvent(event);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST,
                    entity(payload.toString(), sync.getAccessToken()), String.class);
            JsonNode node = objectMapper.readTree(resp.getBody());
            String id = node.get("id").asText();
            log.info("Created Outlook event {} in calendar {}", id, calendarId);
            return id;
        } catch (Exception e) {
            throw new CalendarException("CREATE_EVENT_FAILED",
                    "Failed to create Outlook event: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateEvent(CustomerUserSyncEntity sync, String calendarId, String externalEventId, EventDto event) {
        String url = GRAPH + "/me/events/" + externalEventId;
        try {
            ObjectNode payload = toGraphEvent(event);
            restTemplate.exchange(url, HttpMethod.PATCH,
                    entity(payload.toString(), sync.getAccessToken()), String.class);
            log.info("Updated Outlook event {}", externalEventId);
        } catch (Exception e) {
            throw new CalendarException("UPDATE_EVENT_FAILED",
                    "Failed to update Outlook event: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteEvent(CustomerUserSyncEntity sync, String calendarId, String externalEventId) {
        String url = GRAPH + "/me/events/" + externalEventId;
        try {
            restTemplate.exchange(url, HttpMethod.DELETE,
                    new HttpEntity<>(headers(sync.getAccessToken())), String.class);
            log.info("Deleted Outlook event {}", externalEventId);
        } catch (Exception e) {
            throw new CalendarException("DELETE_EVENT_FAILED",
                    "Failed to delete Outlook event: " + e.getMessage(), e);
        }
    }

    @Override
    public EventDto getEvent(CustomerUserSyncEntity sync, String calendarId, String externalEventId) {
        String url = GRAPH + "/me/events/" + externalEventId
                + "?$select=id,subject,bodyPreview,body,location,organizer,start,end,isAllDay,isCancelled"
                + ",lastModifiedDateTime,createdDateTime,attendees,recurrence,onlineMeeting,webLink";
        try {
            ResponseEntity<String> resp = get(url, sync.getAccessToken());
            JsonNode node = objectMapper.readTree(resp.getBody());
            return parseGraphEvent(node);
        } catch (Exception e) {
            throw new CalendarException("GET_EVENT_FAILED",
                    "Failed to get Outlook event: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Webhook (Graph subscriptions)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public WebhookInfo registerWebhook(CustomerUserSyncEntity sync, String calendarId, String callbackUrl) {
        log.info("Registering Outlook subscription for calendar {} -> {}", calendarId, callbackUrl);
        try {
            String effectiveId = resolveCalendarId(calendarId);
            String resource = effectiveId != null
                    ? "me/calendars/" + effectiveId + "/events"
                    : "me/events";

            ObjectNode body = objectMapper.createObjectNode();
            body.put("changeType", "created,updated,deleted");
            body.put("notificationUrl", callbackUrl);
            body.put("resource", resource);
            body.put("expirationDateTime",
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).toString());
            body.put("clientState", sync.getId().toString());

            ResponseEntity<String> resp = restTemplate.exchange(
                    GRAPH + "/subscriptions", HttpMethod.POST,
                    entity(body.toString(), sync.getAccessToken()), String.class);

            JsonNode node = objectMapper.readTree(resp.getBody());
            String subscriptionId = node.get("id").asText();
            OffsetDateTime expiry = OffsetDateTime.parse(node.get("expirationDateTime").asText());
            log.info("Outlook subscription registered: id={} expiry={}", subscriptionId, expiry);
            return new WebhookInfo(subscriptionId, null, expiry);
        } catch (Exception e) {
            log.error("Failed to register Outlook subscription: {}", e.getMessage());
            throw new CalendarException("WEBHOOK_REGISTRATION_FAILED",
                    "Failed to register Outlook subscription: " + e.getMessage(), e);
        }
    }

    @Override
    public WebhookInfo renewWebhook(CustomerUserSyncEntity sync, String channelId, String calendarId, String callbackUrl) {
        log.info("Renewing Outlook subscription {}", channelId);
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("expirationDateTime",
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).toString());

            ResponseEntity<String> resp = restTemplate.exchange(
                    GRAPH + "/subscriptions/" + channelId, HttpMethod.PATCH,
                    entity(body.toString(), sync.getAccessToken()), String.class);

            JsonNode node = objectMapper.readTree(resp.getBody());
            OffsetDateTime expiry = OffsetDateTime.parse(node.get("expirationDateTime").asText());
            log.info("Outlook subscription {} renewed to {}", channelId, expiry);
            return new WebhookInfo(channelId, null, expiry);
        } catch (Exception e) {
            log.warn("Failed to renew Outlook subscription {}: {}", channelId, e.getMessage());
            return registerWebhook(sync, calendarId, callbackUrl);
        }
    }

    @Override
    public void deleteWebhook(CustomerUserSyncEntity sync, String channelId, String resourceId) {
        log.info("Deleting Outlook subscription {}", channelId);
        try {
            restTemplate.exchange(GRAPH + "/subscriptions/" + channelId, HttpMethod.DELETE,
                    new HttpEntity<>(headers(sync.getAccessToken())), String.class);
        } catch (Exception e) {
            log.warn("Failed to delete Outlook subscription {}: {}", channelId, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getUserEmail / parseEvent
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String getUserEmail(String accessToken) {
        throw new UnsupportedOperationException("Use OutlookOAuthProvider.getUserEmail for profile lookups");
    }

    @Override
    public EventDto parseEvent(Object providerEvent) {
        if (!(providerEvent instanceof JsonNode))
            throw new IllegalArgumentException("Expected JsonNode");
        return parseGraphEvent((JsonNode) providerEvent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private List<EventDto> fetchPaged(String firstUrl, String accessToken) throws Exception {
        List<EventDto> out = new ArrayList<>();
        String next = firstUrl;
        while (next != null) {
            ResponseEntity<String> resp = get(next, accessToken);
            JsonNode root = objectMapper.readTree(resp.getBody());
            for (JsonNode n : values(root)) out.add(parseGraphEvent(n));
            next = root.has("@odata.nextLink") ? root.get("@odata.nextLink").asText() : null;
        }
        return out;
    }

    EventDto parseGraphEvent(JsonNode node) {
        boolean cancelled = node.path("isCancelled").asBoolean(false);
        boolean allDay = node.path("isAllDay").asBoolean(false);

        OffsetDateTime start = parseGraphDt(node.path("start"));
        OffsetDateTime end = parseGraphDt(node.path("end"));
        OffsetDateTime updated = parseIso(node.path("lastModifiedDateTime").asText(null));
        OffsetDateTime created = parseIso(node.path("createdDateTime").asText(null));

        String organizer = node.path("organizer").path("emailAddress").path("address").asText(null);
        String location = node.path("location").path("displayName").asText(null);

        // Attendees
        List<AttendeeDto> attendees = new ArrayList<>();
        for (JsonNode a : node.path("attendees")) {
            attendees.add(AttendeeDto.builder()
                    .email(a.path("emailAddress").path("address").asText(null))
                    .name(a.path("emailAddress").path("name").asText(null))
                    .status(mapOutlookResponse(a.path("status").path("response").asText("notResponded")))
                    .build());
        }

        // Recurrence
        List<String> recurrenceRules = null;
        if (!node.path("recurrence").isMissingNode() && !node.path("recurrence").isNull()) {
            try {
                recurrenceRules = List.of(objectMapper.writeValueAsString(node.get("recurrence")));
            } catch (Exception ignored) {}
        }

        // Online meeting
        String onlineMeetingData = null;
        if (!node.path("onlineMeeting").isMissingNode() && !node.path("onlineMeeting").isNull()) {
            try {
                onlineMeetingData = objectMapper.writeValueAsString(node.get("onlineMeeting"));
            } catch (Exception ignored) {}
        }

        // Use full body content (strip HTML); fall back to bodyPreview (255-char truncated plain text)
        String body = null;
        JsonNode bodyNode = node.path("body");
        if (!bodyNode.isMissingNode() && !bodyNode.isNull()) {
            String content = bodyNode.path("content").asText(null);
            if (content != null && !content.isBlank()) {
                body = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                if (body.length() > 1000) body = body.substring(0, 1000);
            }
        }
        if (body == null || body.isBlank()) {
            body = node.path("bodyPreview").asText(null);
        }
        String webLink = node.path("webLink").asText(null);
        String timeZone = node.path("start").path("timeZone").asText("UTC");

        return EventDto.builder()
                .externalId(node.path("id").asText())
                .title(node.path("subject").asText(""))
                .description(body)
                .location(location)
                .organizer(organizer)
                .startTime(start)
                .endTime(end)
                .timeZoneId(timeZone)
                .allDay(allDay)
                .status(cancelled ? "CANCELLED" : "CONFIRMED")
                .isCancelled(cancelled)
                .provider(ProviderType.OUTLOOK)
                .source(EventSource.OUTLOOK)
                .externalUpdatedAt(updated)
                .createdAt(created)
                .attendees(attendees)
                .recurrenceRules(recurrenceRules)
                .conferenceData(onlineMeetingData)
                .htmlLink(webLink)
                .visibility(node.path("sensitivity").asText(null))
                .build();
    }

    private ObjectNode toGraphEvent(EventDto event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("subject", event.getTitle());
        if (event.getDescription() != null) {
            ObjectNode body = node.putObject("body");
            body.put("contentType", "text");
            body.put("content", event.getDescription());
        }
        if (event.getLocation() != null) {
            node.putObject("location").put("displayName", event.getLocation());
        }
        node.put("isAllDay", event.isAllDay());
        if (event.getStartTime() != null) {
            ObjectNode s = node.putObject("start");
            s.put("dateTime", event.getStartTime().withOffsetSameInstant(ZoneOffset.UTC).toString());
            s.put("timeZone", "UTC");
        }
        if (event.getEndTime() != null) {
            ObjectNode e = node.putObject("end");
            e.put("dateTime", event.getEndTime().withOffsetSameInstant(ZoneOffset.UTC).toString());
            e.put("timeZone", "UTC");
        }
        if (event.getAttendees() != null && !event.getAttendees().isEmpty()) {
            ArrayNode arr = node.putArray("attendees");
            for (AttendeeDto a : event.getAttendees()) {
                ObjectNode item = arr.addObject();
                item.putObject("emailAddress")
                        .put("address", a.getEmail())
                        .put("name", a.getName() != null ? a.getName() : "");
                item.put("type", "required");
            }
        }
        return node;
    }

    private ResponseEntity<String> get(String url, String accessToken) {
        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers(accessToken)), String.class);
    }

    private HttpEntity<String> entity(String body, String accessToken) {
        return new HttpEntity<>(body, headers(accessToken));
    }

    /**
     * Outlook calendar IDs are GUIDs. "primary" is a Google-only concept.
     * When "primary" is passed, fall back to null so /me/events is used (Outlook's primary calendar).
     */
    private static String resolveCalendarId(String calendarId) {
        return (calendarId == null || "primary".equalsIgnoreCase(calendarId)) ? null : calendarId;
    }

    private static HttpHeaders headers(String accessToken) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        h.setBearerAuth(accessToken);
        return h;
    }

    private static Iterable<JsonNode> values(JsonNode root) {
        JsonNode v = root.get("value");
        return (v != null && v.isArray()) ? v : Collections.emptyList();
    }

    private static OffsetDateTime parseGraphDt(JsonNode node) {
        String dt = node.path("dateTime").asText(null);
        String tz = node.path("timeZone").asText("UTC");
        if (dt == null || dt.isBlank()) return null;
        try {
            return OffsetDateTime.parse(dt).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(dt);
                ZoneId zone;
                try { zone = ZoneId.of(tz); } catch (Exception e2) { zone = ZoneOffset.UTC; }
                return ldt.atZone(zone).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static OffsetDateTime parseIso(String s) {
        if (s == null || s.isBlank()) return null;
        try { return OffsetDateTime.parse(s).withOffsetSameInstant(ZoneOffset.UTC); }
        catch (Exception e) { return null; }
    }

    private static String mapOutlookResponse(String r) {
        if (r == null) return "PENDING";
        return switch (r.toLowerCase()) {
            case "accepted" -> "ACCEPTED";
            case "declined" -> "DECLINED";
            case "tentativelyaccepted" -> "TENTATIVE";
            default -> "PENDING";
        };
    }
}
