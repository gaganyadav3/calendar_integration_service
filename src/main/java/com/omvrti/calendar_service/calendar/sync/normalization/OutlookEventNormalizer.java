package com.omvrti.calendar_service.calendar.sync.normalization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omvrti.calendar_service.common.enums.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OutlookEventNormalizer {

    private final ObjectMapper objectMapper;

    public NormalizedEventDTO normalize(JsonNode node) {
        boolean removed = node.has("@removed");
        boolean cancelled = removed || node.path("isCancelled").asBoolean(false);
        List<NormalizedGuestDTO> attendees = new ArrayList<>();
        for (JsonNode a : node.path("attendees")) {
            attendees.add(NormalizedGuestDTO.builder()
                    .email(a.path("emailAddress").path("address").asText(null))
                    .name(a.path("emailAddress").path("name").asText(null))
                    .status(mapOutlookResponse(a.path("status").path("response").asText("notResponded")))
                    .optional("optional".equalsIgnoreCase(a.path("type").asText("")))
                    .organizer(false)
                    .resource(false)
                    .build());
        }

        String meetingUrl = node.path("onlineMeeting").path("joinUrl").asText(null);
        if (meetingUrl == null || meetingUrl.isBlank()) {
            String top = node.path("onlineMeetingUrl").asText(null);
            if (top != null && !top.isBlank()) meetingUrl = top;
        }
        String onlineMeetingData = null;
        if (!node.path("onlineMeeting").isMissingNode() && !node.path("onlineMeeting").isNull()) {
            try { onlineMeetingData = objectMapper.writeValueAsString(node.get("onlineMeeting")); } catch (Exception ignored) {}
        }

        List<String> recurrenceRules = null;
        if (!node.path("recurrence").isMissingNode() && !node.path("recurrence").isNull()) {
            try { recurrenceRules = List.of(objectMapper.writeValueAsString(node.get("recurrence"))); } catch (Exception ignored) {}
        }
        String recurringEventId = node.path("seriesMasterId").asText(null);
        if (recurringEventId != null && recurringEventId.isBlank()) recurringEventId = null;

        return NormalizedEventDTO.builder()
                .externalId(node.path("id").asText())
                .iCalUID(node.path("iCalUId").asText(null))
                .recurringEventId(recurringEventId)
                .title(node.path("subject").asText(""))
                .description(extractBody(node))
                .location(node.path("location").path("displayName").asText(null))
                .organizerEmail(node.path("organizer").path("emailAddress").path("address").asText(null))
                .meetingUrl(meetingUrl)
                .startTime(parseGraphDt(node.path("start")))
                .endTime(parseGraphDt(node.path("end")))
                .timeZoneId(node.path("start").path("timeZone").asText("UTC"))
                .allDay(node.path("isAllDay").asBoolean(false))
                .status(cancelled ? "CANCELLED" : "CONFIRMED")
                .cancelled(cancelled)
                .provider(ProviderType.OUTLOOK)
                .externalUpdatedAt(parseIso(node.path("lastModifiedDateTime").asText(null)))
                .createdAt(parseIso(node.path("createdDateTime").asText(null)))
                .attendees(attendees)
                .recurrenceRules(recurrenceRules)
                .conferenceData(onlineMeetingData)
                .htmlLink(node.path("webLink").asText(null))
                .visibility(node.path("sensitivity").asText(null))
                .originalStartDate(parseGraphDt(node.path("originalStart")))
                .originalStartTimezone(node.path("originalStart").path("timeZone").asText(null))
                .build();
    }

    private static String extractBody(JsonNode node) {
        JsonNode bodyNode = node.path("body");
        if (!bodyNode.isMissingNode() && !bodyNode.isNull()) {
            String content = bodyNode.path("content").asText(null);
            if (content != null && !content.isBlank()) {
                String body = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                return body.length() > 1000 ? body.substring(0, 1000) : body;
            }
        }
        return node.path("bodyPreview").asText(null);
    }

    private static OffsetDateTime parseGraphDt(JsonNode node) {
        String dt = node.path("dateTime").asText(null);
        String tz = node.path("timeZone").asText("UTC");
        if (dt == null || dt.isBlank()) return null;
        try { return OffsetDateTime.parse(dt).withOffsetSameInstant(ZoneOffset.UTC); }
        catch (Exception ignored) {
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
