package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Maps to CU_SYNC_CALENDAR_EVENT Oracle table.
 * EVENT_START_TIME_ZONE / EVENT_END_TIME_ZONE are TIMESTAMP WITH TIME ZONE → OffsetDateTime.
 * EVENT_START_DATE / EVENT_END_DATE are Oracle DATE → LocalDateTime.
 */
@Entity
@Table(name = "CU_SYNC_CALENDAR_EVENT", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"CU_SYNC_CALENDAR_ID", "CALENDAR_EVENT_REFERENCE"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CUSyncCalendarEventEntity {

    @Id
    @SequenceGenerator(name = "cu_event_seq", sequenceName = "CU_SYNC_CALENDAR_EVENT_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cu_event_seq")
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CU_SYNC_CALENDAR_ID", nullable = false)
    private CUSyncCalendarEntity cuSyncCalendar;

    @Column(name = "CALENDAR_EVENT_REFERENCE", nullable = false, length = 1000)
    private String calendarEventReference;

    @Column(name = "RECURRENCE_EVENT_ID", length = 500)
    private String recurrenceEventId;

    @Column(name = "TITLE", length = 1000)
    private String title;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Column(name = "LOCATION", length = 2000)
    private String location;

    @Column(name = "AIRPORT_ID")
    private Integer airportId;

    @Column(name = "MEETING_URL", length = 2000)
    private String meetingUrl;

    @Column(name = "EVENT_START_DATE")
    private LocalDateTime startDate;

    @Column(name = "EVENT_END_DATE")
    private LocalDateTime endDate;

    @Column(name = "EVENT_START_TIME_ZONE")
    private OffsetDateTime startTimeWithZone;

    @Column(name = "EVENT_END_TIME_ZONE")
    private OffsetDateTime endTimeWithZone;

    @Column(name = "IS_ALL_DAY_EVENT")
    private Integer isAllDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CALENDAR_EVENT_STATUS_ID")
    private CalendarEventStatusEntity calendarEventStatus;

    @Column(name = "IS_VISIBLE")
    private Integer isVisible;

    @Column(name = "ORGANIZER_EMAIL", length = 100)
    private String organizerEmail;

    @Column(name = "IS_ORGANIZER")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Integer isOrganizer;

    @Column(name = "LAST_SYNC_DATE")
    private LocalDateTime lastSyncDate;

    @Column(name = "MEETING_MODE")
    private Integer meetingMode;

    @Column(name = "MEETING_ADDRESS_LINE1", length = 200)
    private String meetingAddressLine1;

    @Column(name = "MEETING_ADDRESS_LINE2", length = 200)
    private String meetingAddressLine2;

    @Column(name = "MEETING_COUNTRY_ID")
    private Long meetingCountryId;

    @Column(name = "VISIBILITY", length = 1000)
    private String visibility;

    @Column(name = "INSERTED_ON")
    private LocalDateTime insertedOn;

    @Column(name = "UPDATED_ON")
    private LocalDateTime updatedOn;

    @PrePersist
    protected void onCreate() {
        if (insertedOn == null) insertedOn = LocalDateTime.now();
        if (updatedOn == null) updatedOn = LocalDateTime.now();
        if (isOrganizer == null) isOrganizer = 0;
        if (meetingMode == null || meetingMode < 1) meetingMode = 2; // Oracle constraint: MEETING_MODE >= 1; guard null AND 0
        if (isAllDay == null) isAllDay = 0;
        if (isVisible == null) isVisible = 1;
        if (visibility == null) visibility = "DEFAULT";
        if (startDate == null && startTimeWithZone != null) startDate = startTimeWithZone.toLocalDateTime();
        if (endDate == null && endTimeWithZone != null) endDate = endTimeWithZone.toLocalDateTime();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedOn = LocalDateTime.now();
        if (meetingMode == null || meetingMode < 1) meetingMode = 2;
    }

    // ── Backward-compat helpers (used by EventEntityMapper) ──────────────────

    /** Returns the start time with zone — backward compat for mapper. */
    public OffsetDateTime getEventStartDate() { return startTimeWithZone; }

    public void setEventStartDate(OffsetDateTime dt) {
        this.startTimeWithZone = dt;
        if (dt != null) this.startDate = dt.toLocalDateTime();
    }

    /** Returns the end time with zone — backward compat for mapper. */
    public OffsetDateTime getEventEndDate() { return endTimeWithZone; }

    public void setEventEndDate(OffsetDateTime dt) {
        this.endTimeWithZone = dt;
        if (dt != null) this.endDate = dt.toLocalDateTime();
    }

    /** No-op — timezone string is implicit in startTimeWithZone. */
    public String getEventStartTimeZone() { return null; }
    public void setEventStartTimeZone(String tz) { /* no-op */ }

    public String getEventEndTimeZone() { return null; }
    public void setEventEndTimeZone(String tz) { /* no-op */ }

    /** Returns true if the associated CalendarEventStatus marks this as cancelled. */
    public Boolean getIsCancelled() {
        return calendarEventStatus != null && Integer.valueOf(1).equals(calendarEventStatus.getIsCancelled());
    }

    public void setIsCancelled(Boolean cancelled) {
        // Callers should set calendarEventStatus to a status entity with IS_CANCELLED=1
    }

    public Boolean getIsAllDayEvent() { return Integer.valueOf(1).equals(isAllDay); }

    public void setIsAllDayEvent(Boolean allDay) {
        this.isAllDay = Boolean.TRUE.equals(allDay) ? 1 : 0;
    }

    public Boolean getIsVisible() { return !Integer.valueOf(0).equals(isVisible); }

    public void setIsVisible(Boolean visible) {
        this.isVisible = Boolean.TRUE.equals(visible) ? 1 : 0;
    }

    public Boolean getIsOrganizer() { return Integer.valueOf(1).equals(isOrganizer); }

    public void setIsOrganizer(Boolean org) {
        this.isOrganizer = Boolean.TRUE.equals(org) ? 1 : 0;
    }

    // ── Fully transient fields ────────────────────────────────────────────────

    @Transient private String providerEtag;
    @Transient private OffsetDateTime providerUpdatedTimestamp;
    @Transient private OffsetDateTime providerCreatedTimestamp;
    @Transient private String providerStatus;
    @Transient private String recurrenceRule;
    @Transient private String htmlLink;
    @Transient private String transparency;
    @Transient private Integer sequenceVersion;
    @Transient private String conferenceData;
    @Transient private String onlineMeetingData;

    public OffsetDateTime getLastEventSyncDate() { return null; }
    public void setLastEventSyncDate(OffsetDateTime dt) { /* no column */ }
}
