package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps to CUSTOMER_USER_SYNC Oracle table.
 * ACCESS_TOKEN and REFRESH_TOKEN are VARCHAR2(4000) — stored as plain Strings (full length, no truncation).
 * ACCESS_TOKEN_EXPIRY_DATE is Oracle DATE — mapped as LocalDateTime.
 */
@Entity
@Table(name = "CUSTOMER_USER_SYNC", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"CUSTOMER_USER_ID", "SYNC_VENDOR_ID"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerUserSyncEntity {

    @Id
    @SequenceGenerator(name = "cus_seq", sequenceName = "CUSTOMER_USER_SYNC_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cus_seq")
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CUSTOMER_USER_ID", nullable = false)
    private CustomerUserEntity customerUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SYNC_VENDOR_ID", nullable = false)
    private SyncVendorEntity syncVendor;

    @Column(name = "SYNC_ACCOUNT_REFERENCE", length = 500, nullable = false)
    private String syncingAccountReference;

    @Column(name = "SYNC_EMAIL", length = 200, nullable = false)
    private String syncEmail;

    @Column(name = "DISPLAY_NAME", length = 255)
    private String displayName;

    // Tokens stored as VARCHAR2(4000) — full length, no truncation (Google ~150 chars, Outlook JWT ~1500 chars)
    @Column(name = "ACCESS_TOKEN", length = 4000)
    private String accessToken;

    @Column(name = "REFRESH_TOKEN", length = 4000)
    private String refreshToken;

    @Column(name = "ACCESS_TOKEN_EXPIRY_DATE")
    private LocalDateTime accessTokenExpiryDate;

    @Column(name = "TOKEN_SCOPE", length = 1000)
    private String tokenScope;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SYNC_STATUS_ID", nullable = false)
    private SyncStatusEntity syncStatus;

    @Column(name = "LAST_CALENDAR_SYNC_DATE")
    private LocalDateTime lastSyncDate;

    @Column(name = "ERROR_CODE", length = 100)
    private String errorCode;

    @Column(name = "ERROR_MESSAGE", length = 4000)
    private String errorMessage;

    @Column(name = "SYNC_HISTORY", length = 4000)
    private String syncHistory;

    @Column(name = "INSERTED_ON", nullable = false)
    private LocalDateTime insertedOn;

    @Column(name = "UPDATED_ON", nullable = false)
    private LocalDateTime updatedOn;

    // ── Transient helpers ─────────────────────────────────────────────────────

    @Transient private String idToken;

    public String getSyncEmail() { return syncEmail; }

    public void setSyncEmail(String email) {
        this.syncEmail = email;
        // Also populate SYNC_ACCOUNT_REFERENCE if not yet set
        if (this.syncingAccountReference == null && email != null) {
            this.syncingAccountReference = email;
        }
    }

    public Integer getIsActive() {
        if (syncStatus == null) return 0;
        return Integer.valueOf(1).equals(syncStatus.getIsActive()) ? 1 : 0;
    }

    public void setIsActive(Integer active) {
        // Managed through SYNC_STATUS_ID / syncStatus FK — no-op
    }

    public void setAccessTokenExpiryDate(java.time.OffsetDateTime dt) {
        this.accessTokenExpiryDate = dt != null ? dt.toLocalDateTime() : null;
    }

    // ── Token expiry check ────────────────────────────────────────────────────

    public boolean isTokenExpired() {
        if (accessTokenExpiryDate == null) return true;
        return LocalDateTime.now().isAfter(accessTokenExpiryDate.minusMinutes(5));
    }

    @PrePersist
    protected void onCreate() {
        if (insertedOn == null) insertedOn = LocalDateTime.now();
        if (updatedOn == null) updatedOn = LocalDateTime.now();
        if (syncingAccountReference == null && syncEmail != null) syncingAccountReference = syncEmail;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedOn = LocalDateTime.now();
    }
}
