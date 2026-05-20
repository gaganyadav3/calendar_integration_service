package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps to EVENT_REMINDER Oracle table.
 */
@Entity
@Table(name = "EVENT_REMINDER", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"CU_SYNC_CALENDAR_EVENT_ID", "TIME_VALUE"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventReminderEntity {

    @Id
    @SequenceGenerator(name = "event_reminder_seq", sequenceName = "EVENT_REMINDER_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "event_reminder_seq")
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CU_SYNC_CALENDAR_EVENT_ID", nullable = false)
    private CUSyncCalendarEventEntity cuSyncCalendarEvent;

    @Column(name = "NOTIFICATION_MEDIUM", nullable = false)
    private Integer notificationMedium;

    @Column(name = "TIME_VALUE", nullable = false)
    private Integer timeValue;

    @Column(name = "TIME_UNIT_ID", nullable = false)
    private Integer timeUnitId;

    @Column(name = "INSERTED_ON")
    private LocalDateTime insertedOn;

    @Column(name = "UPDATED_ON")
    private LocalDateTime updatedOn;

    @PrePersist
    protected void onCreate() {
        if (insertedOn == null) insertedOn = LocalDateTime.now();
        if (updatedOn == null) updatedOn = LocalDateTime.now();
        if (notificationMedium == null) notificationMedium = 1;
        if (timeUnitId == null) timeUnitId = 1;
        if (timeValue == null) timeValue = 10;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedOn = LocalDateTime.now();
    }

    // ── Backward-compat setter (mapper passes String method name) ─────────────

    public void setNotificationMedium(String medium) {
        this.notificationMedium = 1; // default popup; String values not stored in new schema
    }
}
