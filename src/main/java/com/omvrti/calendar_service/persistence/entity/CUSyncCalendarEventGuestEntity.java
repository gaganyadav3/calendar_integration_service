package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps to CU_SYNC_CALENDAR_EVENT_GUEST Oracle table.
 * IS_ORGANISER uses British spelling as in the DB column.
 */
@Entity
@Table(name = "CU_SYNC_CALENDAR_EVENT_GUEST", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"CU_SYNC_CAL_EVENT_ID", "GUEST_EMAIL"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CUSyncCalendarEventGuestEntity {

    @Id
    @SequenceGenerator(name = "cu_guest_seq", sequenceName = "CU_SYNC_CALENDAR_EVENT_GUEST_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cu_guest_seq")
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CU_SYNC_CAL_EVENT_ID", nullable = false)
    private CUSyncCalendarEventEntity cuSyncCalendarEvent;

    @Column(name = "GUEST_NAME", length = 255)
    private String guestName;

    @Column(name = "GUEST_EMAIL", length = 1000)
    private String guestEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EVENT_GUEST_RESPONSE_ID")
    private EventGuestResponseEntity guestResponse;

    @Column(name = "IS_OPTIONAL")
    private Integer isOptional;

    @Column(name = "IS_ORGANISER")
    private Integer isOrganizer;

    @Column(name = "IS_HUMAN")
    private Integer isHuman;

    @Column(name = "COMMENT_TEXT", length = 4000)
    private String commentText;
    @Column(name = "IS_ACTIVE")
    private Integer isActive;
    @Column(name = "IS_DELETED")
    private Integer isDeleted;
    @Column(name = "DELETED_ON")
    private LocalDateTime deletedOn;

    @Column(name = "INSERTED_ON")
    private LocalDateTime insertedOn;

    @Column(name = "UPDATED_ON")
    private LocalDateTime updatedOn;

    @PrePersist
    protected void onCreate() {
        if (insertedOn == null) insertedOn = LocalDateTime.now();
        if (updatedOn == null) updatedOn = LocalDateTime.now();
        if (isOptional == null) isOptional = 0;
        if (isOrganizer == null) isOrganizer = 0;
        if (isHuman == null) isHuman = 1;
        if (isActive == null) isActive = 1;
        if (isDeleted == null) isDeleted = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedOn = LocalDateTime.now();
    }

    // ── Backward-compat helpers ───────────────────────────────────────────────

    @Transient private String responseStatus;

    public Boolean getIsOptionalBoolean() { return Integer.valueOf(1).equals(isOptional); }

    public void setIsOptional(Boolean opt) { this.isOptional = Boolean.TRUE.equals(opt) ? 1 : 0; }

    public Boolean getIsOrganizerBoolean() { return Integer.valueOf(1).equals(isOrganizer); }

    public void setIsOrganiser(Boolean org) { this.isOrganizer = Boolean.TRUE.equals(org) ? 1 : 0; }

    public void setIsOrganizer(Boolean org) { this.isOrganizer = Boolean.TRUE.equals(org) ? 1 : 0; }

    public void setIsHuman(Boolean human) { this.isHuman = Boolean.TRUE.equals(human) ? 1 : 0; }
}
