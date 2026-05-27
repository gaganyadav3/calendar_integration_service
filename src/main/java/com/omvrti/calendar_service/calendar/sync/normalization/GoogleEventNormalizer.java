package com.omvrti.calendar_service.calendar.sync.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.omvrti.calendar_service.common.enums.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class GoogleEventNormalizer {

    private final ObjectMapper objectMapper;

    public NormalizedEventDTO normalize(Event event) {
        boolean cancelled = "cancelled".equalsIgnoreCase(event.getStatus());
        OffsetDateTime start = parseDateTime(event.getStart());
        OffsetDateTime end = parseDateTime(event.getEnd());
        boolean allDay = event.getStart() != null && event.getStart().getDate() != null;
        OffsetDateTime updated = event.getUpdated() != null
                ? OffsetDateTime.ofInstant(Instant.ofEpochMilli(event.getUpdated().getValue()), ZoneOffset.UTC) : null;
        OffsetDateTime created = event.getCreated() != null
                ? OffsetDateTime.ofInstant(Instant.ofEpochMilli(event.getCreated().getValue()), ZoneOffset.UTC) : null;

        List<NormalizedGuestDTO> guests = new ArrayList<>();
        if (event.getAttendees() != null) {
            event.getAttendees().forEach(a -> guests.add(NormalizedGuestDTO.builder()
                    .email(a.getEmail())
                    .name(a.getDisplayName())
                    .status(mapGoogleResponseStatus(a.getResponseStatus()))
                    .optional(Boolean.TRUE.equals(a.getOptional()))
                    .organizer(Boolean.TRUE.equals(a.getOrganizer()))
                    .resource(Boolean.TRUE.equals(a.getResource()))
                    .comment(a.getComment())
                    .build()));
        }

        String conferenceData = null;
        String meetingUrl = null;
        if (event.getConferenceData() != null) {
            try { conferenceData = objectMapper.writeValueAsString(event.getConferenceData()); } catch (Exception ignored) {}
            if (event.getConferenceData().getEntryPoints() != null) {
                var eps = event.getConferenceData().getEntryPoints();
                meetingUrl = eps.stream().filter(ep -> "video".equals(ep.getEntryPointType()))
                        .map(ep -> (String) ep.getUri()).filter(Objects::nonNull).findFirst().orElse(null);
                if (meetingUrl == null) {
                    meetingUrl = eps.stream().map(ep -> (String) ep.getUri())
                            .filter(Objects::nonNull).findFirst().orElse(null);
                }
            }
        }
        if (meetingUrl == null || meetingUrl.isBlank()) {
            meetingUrl = event.getLocation();
        }
        if ((meetingUrl == null || meetingUrl.isBlank()) && event.getDescription() != null) {
            meetingUrl = event.getDescription();
        }

        OffsetDateTime originalStart = event.getOriginalStartTime() != null
                ? parseDateTime(event.getOriginalStartTime()) : null;
        String originalStartTz = event.getOriginalStartTime() != null ? event.getOriginalStartTime().getTimeZone() : null;

        return NormalizedEventDTO.builder()
                .externalId(event.getId())
                .iCalUID(event.getICalUID())
                .recurringEventId(event.getRecurringEventId())
                .title(event.getSummary() != null ? event.getSummary() : "")
                .description(event.getDescription())
                .location(event.getLocation())
                .organizerEmail(event.getOrganizer() != null ? event.getOrganizer().getEmail() : null)
                .meetingUrl(meetingUrl)
                .startTime(start)
                .endTime(end)
                .timeZoneId(event.getStart() != null && event.getStart().getTimeZone() != null ? event.getStart().getTimeZone() : "UTC")
                .allDay(allDay)
                .status(event.getStatus())
                .cancelled(cancelled)
                .provider(ProviderType.GOOGLE)
                .externalUpdatedAt(updated)
                .createdAt(created)
                .attendees(guests)
                .recurrenceRules(event.getRecurrence() != null ? new ArrayList<>(event.getRecurrence()) : null)
                .conferenceData(conferenceData)
                .htmlLink(event.getHtmlLink())
                .sequence(event.getSequence())
                .etag(event.getEtag())
                .transparency(event.getTransparency())
                .visibility(event.getVisibility())
                .originalStartDate(originalStart)
                .originalStartTimezone(originalStartTz)
                .build();
    }

    private static OffsetDateTime parseDateTime(EventDateTime edt) {
        if (edt == null) return null;
        if (edt.getDateTime() != null) {
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(edt.getDateTime().getValue()), ZoneOffset.UTC);
        }
        if (edt.getDate() != null) {
            LocalDate d = LocalDate.parse(edt.getDate().toStringRfc3339().substring(0, 10));
            return d.atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        return null;
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
