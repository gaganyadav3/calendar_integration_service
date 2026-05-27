package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Maps to CU_SYNC_CALENDAR Oracle table.
 */
@Entity
@Table(name = "CU_SYNC_CALENDAR", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"CUSTOMER_USER_SYNC_ID", "CALENDAR_REFERENCE"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CUSyncCalendarEntity {

    @Id
    @SequenceGenerator(name = "cu_sync_cal_seq", sequenceName = "CU_SYNC_CALENDAR_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cu_sync_cal_seq")
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CUSTOMER_USER_SYNC_ID", nullable = false)
    private CustomerUserSyncEntity customerUserSync;

    @Column(name = "CALENDAR_REFERENCE", nullable = false, length = 1000)
    private String calendarReference;

    @Column(name = "DISPLAY_NAME", length = 200)
    private String displayName;

    @Column(name = "COLOR", length = 20)
    private String color;

    @Column(name = "TIME_ZONE", length = 100)
    private String timeZone;

    @Column(name = "IS_PRIMARY")
    private Integer isPrimary;

    @Column(name = "IS_WRITABLE")
    private Integer isWritable;

    // IS_ENABLED maps to the actual IS_SYNC_ON column in Oracle
    @Column(name = "IS_SYNC_ON")
    private Integer isEnabled;

    @Column(name = "LAST_EVENT_SYNC_DATE")
    private LocalDateTime lastEventSyncTimestamp;

    /** Provider sync token — Google nextSyncToken or Outlook @odata.deltaLink. VARCHAR in DB. */
    @Column(name = "SYNC_CURSOR", length = 2000)
    private String syncCursor;
    @Column(name = "SOURCE_SYSTEM", length = 50)
    private String sourceSystem;

    @Column(name = "INSERTED_ON", nullable = false)
    private LocalDateTime insertedOn;

    @Column(name = "UPDATED_ON")
    private LocalDateTime updatedOn;

    @PrePersist
    protected void onCreate() {
        if (insertedOn == null) insertedOn = LocalDateTime.now();
        if (updatedOn == null) updatedOn = LocalDateTime.now();
        if (isEnabled == null) isEnabled = 1;
        if (isPrimary == null) isPrimary = 0;
        if (isWritable == null) isWritable = 0;
        if (lastEventSyncTimestamp == null) lastEventSyncTimestamp = LocalDateTime.now().minusDays(90);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedOn = LocalDateTime.now();
    }

    // ── Backward-compat helpers ───────────────────────────────────────────────

    public Boolean getIsPrimaryBoolean() {
        return Integer.valueOf(1).equals(isPrimary);
    }

    public Boolean getIsSyncOn() {
        return Integer.valueOf(1).equals(isEnabled);
    }

    public void setIsSyncOn(Boolean on) {
        this.isEnabled = Boolean.TRUE.equals(on) ? 1 : 0;
    }

    public void setIsWritable(Boolean writable) {
        this.isWritable = Boolean.TRUE.equals(writable) ? 1 : 0;
    }

    /** Returns last sync time as OffsetDateTime for provider calls that need it. */
    public OffsetDateTime getLastEventSyncDate() {
        return lastEventSyncTimestamp != null
                ? lastEventSyncTimestamp.atZone(java.time.ZoneOffset.UTC).toOffsetDateTime()
                : null;
    }

    public void setLastEventSyncDate(OffsetDateTime dt) {
        this.lastEventSyncTimestamp = dt != null ? dt.toLocalDateTime() : null;
    }
}
