package com.omvrti.calendar_service.common.util;

import com.omvrti.calendar_service.common.dto.AttendeeDto;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.dto.ReminderDto;
import com.omvrti.calendar_service.persistence.entity.CalendarEventStatusEntity;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEventEntity;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEventGuestEntity;
import com.omvrti.calendar_service.persistence.entity.EventGuestResponseEntity;
import com.omvrti.calendar_service.persistence.entity.EventReminderEntity;
import com.omvrti.calendar_service.persistence.repository.CalendarEventStatusRepository;
import com.omvrti.calendar_service.persistence.repository.CUSyncCalendarEventGuestRepository;
import com.omvrti.calendar_service.persistence.repository.EventGuestResponseRepository;
import com.omvrti.calendar_service.persistence.repository.EventReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventEntityMapper {

    private final CUSyncCalendarEventGuestRepository guestRepository;
    private final EventReminderRepository reminderRepository;
    private final CalendarEventStatusRepository calendarEventStatusRepository;
    private final EventGuestResponseRepository eventGuestResponseRepository;
    private final JdbcTemplate jdbcTemplate;

    /** Cache: "CONFIRMED" / "CANCELLED" → entity. Static master data, safe to cache for app lifetime. */
    private final Map<String, CalendarEventStatusEntity> statusCache = new ConcurrentHashMap<>();

    /** Cache: "ACCEPTED" / "DECLINED" / "TENTATIVE" / "NEEDS_ACTION" → entity. */
    private final Map<String, EventGuestResponseEntity> guestResponseCache = new ConcurrentHashMap<>();

    /** Cached minimum airport ID — AIRPORT_ID is NOT NULL in Oracle; loaded on first event insert. */
    private volatile Integer defaultAirportId;

    private Integer getDefaultAirportId() {
        if (defaultAirportId == null) {
            synchronized (this) {
                if (defaultAirportId == null) {
                    try {
                        Integer id = jdbcTemplate.queryForObject(
                                "SELECT MIN(ID) FROM AIRPORT", Integer.class);
                        defaultAirportId = (id != null) ? id : -1;
                        if (id != null) {
                            log.info("Default AIRPORT_ID loaded: {}", id);
                        } else {
                            log.warn("AIRPORT table is empty — AIRPORT_ID cannot be defaulted; event inserts will fail");
                        }
                    } catch (Exception e) {
                        defaultAirportId = -1;
                        log.warn("Could not query AIRPORT table for default ID: {}", e.getMessage());
                    }
                }
            }
        }
        return defaultAirportId == -1 ? null : defaultAirportId;
    }

    private CalendarEventStatusEntity resolveStatus(boolean cancelled) {
        String key = cancelled ? "CANCELLED" : "CONFIRMED";
        return statusCache.computeIfAbsent(key,
                k -> calendarEventStatusRepository.findByEventStatusCode(k).orElse(null));
    }

    private EventGuestResponseEntity resolveGuestResponse(String status) {
        String key = mapToGuestResponseName(status);
        return guestResponseCache.computeIfAbsent(key,
                k -> eventGuestResponseRepository.findByName(k).orElse(null));
    }

    private static String mapToGuestResponseName(String status) {
        if (status == null) return "NEEDS_ACTION";
        return switch (status.toUpperCase()) {
            case "ACCEPTED" -> "ACCEPTED";
            case "DECLINED" -> "DECLINED";
            case "TENTATIVE" -> "TENTATIVE";
            default -> "NEEDS_ACTION";
        };
    }

    /**
     * Maps provider-specific meeting signals to the Oracle MEETING_MODE column.
     * Oracle constraint: MEETING_MODE >= 1 — never returns 0 or null.
     *
     * Mapping:
     *   conferenceData present (hangoutsMeet / teamsMeeting / onlineMeeting) = 1 (virtual)
     *   physical location present (no online meeting)                         = 2 (in-person)
     *   both online + physical                                                 = 3 (hybrid)
     *   neither (null / unknown)                                              = 2 (default)
     */
    private static Integer normalizeMeetingMode(EventDto dto) {
        boolean hasOnline = dto.getConferenceData() != null && !dto.getConferenceData().isBlank();
        boolean hasLocation = dto.getLocation() != null && !dto.getLocation().isBlank();
        if (hasOnline && hasLocation) return 3; // hybrid
        if (hasOnline)                return 1; // virtual — hangoutsMeet / teamsMeeting / onlineMeeting
        if (hasLocation)              return 2; // in-person — physical address present
        return 2;                               // default; satisfies MEETING_MODE >= 1
    }

    // ── DTO → Entity ──────────────────────────────────────────────────────────

    public CUSyncCalendarEventEntity dtoToEntity(EventDto dto) {
        if (dto == null) return null;
        CUSyncCalendarEventEntity e = new CUSyncCalendarEventEntity();
        applyDto(dto, e);
        return e;
    }

    public void updateEntityFromDto(EventDto dto, CUSyncCalendarEventEntity entity) {
        if (dto == null || entity == null) return;
        applyDto(dto, entity);
    }

    private void applyDto(EventDto dto, CUSyncCalendarEventEntity e) {
        e.setCalendarEventReference(dto.getExternalId());
        e.setTitle(dto.getTitle() != null ? dto.getTitle() : "");
        e.setDescription(dto.getDescription());
        e.setLocation(dto.getLocation());
        // meetingUrl not in EventDto — set separately if needed
        e.setEventStartDate(dto.getStartTime());
        e.setEventEndDate(dto.getEndTime());
        // Timezone string is implicit in the TIMESTAMP WITH TIME ZONE columns — no-op
        e.setEventStartTimeZone(dto.getTimeZoneId());
        e.setEventEndTimeZone(dto.getTimeZoneId());
        e.setIsAllDayEvent(dto.isAllDay());
        e.setOrganizerEmail(dto.getOrganizer());
        e.setIsCancelled(dto.isCancelled());
        e.setProviderStatus(dto.getStatus());
        e.setVisibility(dto.getVisibility() != null ? dto.getVisibility() : "DEFAULT");
        e.setIsVisible(true);
        e.setTransparency(dto.getTransparency());
        e.setSequenceVersion(dto.getSequence());
        e.setProviderEtag(dto.getEtag());
        e.setHtmlLink(dto.getHtmlLink());
        e.setRecurrenceEventId(dto.getRecurringEventId());
        e.setConferenceData(dto.getConferenceData());
        if (dto.getExternalUpdatedAt() != null) e.setProviderUpdatedTimestamp(dto.getExternalUpdatedAt());
        if (dto.getCreatedAt() != null) e.setProviderCreatedTimestamp(dto.getCreatedAt());
        if (dto.getRecurrenceRules() != null && !dto.getRecurrenceRules().isEmpty()) {
            e.setRecurrenceRule(String.join("\n", dto.getRecurrenceRules()));
        }

        // AIRPORT_ID is NOT NULL in Oracle — set a valid FK value from the AIRPORT table
        if (e.getAirportId() == null) {
            Integer aid = getDefaultAirportId();
            if (aid != null) e.setAirportId(aid);
        }

        // Always set CALENDAR_EVENT_STATUS_ID — column is NOT NULL in Oracle
        CalendarEventStatusEntity status = resolveStatus(dto.isCancelled());
        if (status != null) {
            e.setCalendarEventStatus(status);
        } else {
            log.warn("CalendarEventStatus '{}' not found in DB — CALENDAR_EVENT_STATUS_ID will be null. Run master data init.",
                    dto.isCancelled() ? "CANCELLED" : "CONFIRMED");
        }

        // Resolve MEETING_MODE — Oracle constraint: MEETING_MODE >= 1; never store raw provider strings or 0
        e.setMeetingMode(normalizeMeetingMode(dto));

//        log.debug("Mapping event: externalId={}, meetingMode={}, cancelled={}, statusId={}",
//                dto.getExternalId(),
//                e.getMeetingMode(),
//                dto.isCancelled(),
//                e.getCalendarEventStatus() != null ? e.getCalendarEventStatus().getId() : "null");
    }

    // ── Entity → DTO ──────────────────────────────────────────────────────────

    public EventDto entityToDto(CUSyncCalendarEventEntity entity) {
        if (entity == null) return null;
        return EventDto.builder()
                .id(entity.getId())
                .externalId(entity.getCalendarEventReference())
                .recurringEventId(entity.getRecurrenceEventId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .location(entity.getLocation())
                .startTime(entity.getEventStartDate())
                .endTime(entity.getEventEndDate())
                .allDay(Boolean.TRUE.equals(entity.getIsAllDayEvent()))
                .organizer(entity.getOrganizerEmail())
                .isCancelled(Boolean.TRUE.equals(entity.getIsCancelled()))
                .status(entity.getProviderStatus())
                .visibility(entity.getVisibility())
                .transparency(entity.getTransparency())
                .sequence(entity.getSequenceVersion())
                .etag(entity.getProviderEtag())
                .htmlLink(entity.getHtmlLink())
                .conferenceData(entity.getConferenceData())
                .externalUpdatedAt(entity.getProviderUpdatedTimestamp())
                .createdAt(entity.getProviderCreatedTimestamp())
                .updatedAt(entity.getUpdatedOn() != null
                        ? entity.getUpdatedOn().atZone(ZoneOffset.UTC).toOffsetDateTime() : null)
                .build();
    }

    // ── Guest / Reminder sync ─────────────────────────────────────────────────

    public void syncGuests(CUSyncCalendarEventEntity event, List<AttendeeDto> attendees) {
        if (attendees == null) return;
        List<CUSyncCalendarEventGuestEntity> existing = guestRepository.findByCuSyncCalendarEvent(event);
        if (!existing.isEmpty()) {
            guestRepository.deleteAll(existing);
            guestRepository.flush(); // force DELETEs to DB before INSERTs to avoid UK1 violation
        }

        for (AttendeeDto a : attendees) {
            if (a.getEmail() == null) continue;
            EventGuestResponseEntity guestResponse = resolveGuestResponse(a.getStatus());
            if (guestResponse == null) {
                log.warn("EventGuestResponse '{}' not found in DB — skipping guest {}. Run master data init.",
                        mapToGuestResponseName(a.getStatus()), a.getEmail());
                continue;
            }
            boolean isOrganizer = a.getEmail().equalsIgnoreCase(event.getOrganizerEmail());
            CUSyncCalendarEventGuestEntity guest = CUSyncCalendarEventGuestEntity.builder()
                    .cuSyncCalendarEvent(event)
                    .guestEmail(a.getEmail())
                    .guestName(a.getName())
                    .responseStatus(a.getStatus())
                    .guestResponse(guestResponse)
                    .isOrganizer(isOrganizer ? 1 : 0)
                    .isOptional(0)
                    .isHuman(1)
                    .build();
            guestRepository.save(guest);
        }
    }

    public void syncReminders(CUSyncCalendarEventEntity event, List<ReminderDto> reminders) {
        if (reminders == null) return;
        reminderRepository.deleteByCuSyncCalendarEvent(event);
        for (ReminderDto r : reminders) {
            EventReminderEntity reminder = EventReminderEntity.builder()
                    .cuSyncCalendarEvent(event)
                    .notificationMedium(1)
                    .timeValue(r.getMinutes() > 0 ? r.getMinutes() : 10)
                    .timeUnitId(1)
                    .build();
            reminderRepository.save(reminder);
        }
    }
}
