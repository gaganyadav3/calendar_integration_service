package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Maps to OM_EVENT Oracle table.
 * EVENT_START_TIME / EVENT_END_TIME are Oracle DATE → LocalDateTime.
 * EVENT_START_TIMEZONE / EVENT_END_TIMEZONE are TIMESTAMP WITH TIME ZONE → OffsetDateTime.
 */
@Entity
@Table(name = "OM_EVENT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OMEventEntity {

    @Id
    @SequenceGenerator(name = "om_event_seq", sequenceName = "OM_EVENT_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "om_event_seq")
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CUSTOMER_USER_ID", nullable = false)
    private CustomerUserEntity customer;

    @Column(name = "USER_PROMPT_TEXT", length = 400)
    private String userPromptText;

    @Column(name = "EVENT_TITLE", length = 400)
    private String eventTitle;

    @Column(name = "EVENT_DESCRIPTION", length = 4000)
    private String eventDescription;

    @Column(name = "EVENT_START_TIME")
    private LocalDateTime eventStartTime;

    @Column(name = "EVENT_END_TIME")
    private LocalDateTime eventEndTime;

    @Column(name = "EVENT_START_TIMEZONE")
    private OffsetDateTime eventStartTimezone;

    @Column(name = "EVENT_END_TIMEZONE")
    private OffsetDateTime eventEndTimezone;

    @Column(name = "IS_ALL_DAY")
    private Integer isAllDay;

    @Column(name = "MEETING_URL", length = 2000)
    private String meetingUrl;

    @Column(name = "MEETING_PLATFORM")
    private Integer meetingPlatform;

    @Column(name = "LOCATION", length = 2000)
    private String location;

    @Column(name = "IS_RECURRING_EVENT")
    private Integer isRecurringEvent;

    @Column(name = "RECURRING_OM_EVENT_ID")
    private Long recurringOmEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CALENDAR_EVENT_STATUS_ID")
    private CalendarEventStatusEntity calendarEventStatus;

    @Column(name = "IS_VISIBLE")
    private Integer isVisible;

    @Column(name = "ORGANIZER_EMAIL", length = 100)
    private String organizerEmail;

    @Column(name = "IS_ORGANIZER")
    private Integer isOrganizer;

    @Column(name = "CU_SYNC_CALENDAR_ID_LIST", length = 2000)
    private String cuSyncCalendarIdList;

    @Column(name = "AIRPORT_ID")
    private Integer airportId;

    @Column(name = "INSERTED_ON")
    private LocalDateTime insertedOn;

    @Column(name = "UPDATED_ON")
    private LocalDateTime updatedOn;

    @PrePersist
    protected void onCreate() {
        if (insertedOn == null) insertedOn = LocalDateTime.now();
        if (updatedOn == null) updatedOn = LocalDateTime.now();
        if (isAllDay == null) isAllDay = 0;
        if (isRecurringEvent == null) isRecurringEvent = 0;
        if (isVisible == null) isVisible = 1;
        if (isOrganizer == null) isOrganizer = 0;
        if (airportId == null) airportId = 0;
        if (meetingPlatform == null) meetingPlatform = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedOn = LocalDateTime.now();
    }
}
