package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Maps to CU_SYNC_CALENDAR_WEBHOOK Oracle table.
 */
@Entity
@Table(name = "CU_SYNC_CALENDAR_WEBHOOK", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"CU_SYNC_CALENDAR_ID", "WEBHOOK_STATUS_ID"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CUSyncCalendarWebhookEntity {

    @Id
    @SequenceGenerator(name = "cu_webhook_seq", sequenceName = "CU_SYNC_CALENDAR_WEBHOOK_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cu_webhook_seq")
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CU_SYNC_CALENDAR_ID", nullable = false)
    private CUSyncCalendarEntity cuSyncCalendar;

    @Column(name = "EXTERNAL_CHANNEL_ID", length = 500)
    private String externalChannelId;

    @Column(name = "RESOURCE_URL", length = 2000)
    private String resourceUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "WEBHOOK_STATUS_ID")
    private WebhookStatusEntity webhookStatus;

    @Column(name = "EXPIRY_DATE", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "INSERTED_ON")
    private LocalDateTime insertedOn;

    @Column(name = "UPDATED_ON")
    private LocalDateTime updatedOn;
    @Column(name = "IS_ACTIVE")
    private Integer isActive;
    @Column(name = "IS_DELETED")
    private Integer isDeleted;
    @Column(name = "DELETED_ON")
    private LocalDateTime deletedOn;

    @PrePersist
    protected void onCreate() {
        if (insertedOn == null) insertedOn = LocalDateTime.now();
        if (updatedOn == null) updatedOn = LocalDateTime.now();
        if (isActive == null) isActive = 1;
        if (isDeleted == null) isDeleted = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedOn = LocalDateTime.now();
    }

    // ── Backward-compat helpers ───────────────────────────────────────────────

    @Transient private String subscriptionId;

    public OffsetDateTime getExpiryDateAsOffset() {
        return expiryDate != null ? expiryDate.atOffset(ZoneOffset.UTC) : null;
    }

    public boolean isExpired() {
        if (expiryDate == null) return true;
        return LocalDateTime.now().isAfter(expiryDate);
    }

    public boolean needsRenewal() {
        if (expiryDate == null) return true;
        return LocalDateTime.now().isAfter(expiryDate.minusHours(24));
    }
}
