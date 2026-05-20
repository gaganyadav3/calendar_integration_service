package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "SYNC_VENDOR")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncVendorEntity {

    @Id
    @SequenceGenerator(name = "sync_vendor_seq", sequenceName = "SYNC_VENDOR_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sync_vendor_seq")
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = 50)
    private String name;

    @Column(name = "DISPLAY_NAME", nullable = false, length = 50)
    private String displayName;

    @Column(name = "API_AUTH_TYPE", nullable = false)
    private Integer apiAuthType;

    @Column(name = "SCOPES", length = 1000)
    private String scopes;

    @Column(name = "API_BASE_URL", length = 500)
    private String apiBaseUrl;

    @Column(name = "VENDOR_TYPE", nullable = false)
    private Integer vendorType;

    @Column(name = "IS_NEW_CONNECTION", nullable = false)
    private Integer isNewConnection;

    @Column(name = "IS_VALID", nullable = false)
    private Integer isValid;

    @Column(name = "DISPLAY_SORT_ORDER", nullable = false)
    private Integer displaySortOrder;

    @Column(name = "INSERTED_ON", nullable = false)
    private LocalDateTime insertedOn;

    @Column(name = "UPDATED_ON", nullable = false)
    private LocalDateTime updatedOn;

    // ── Backward-compat aliases ───────────────────────────────────────────────

    /** Old field was VENDOR_CODE; maps to NAME. */
    public String getVendorCode() { return name; }

    /** Old field was VENDOR_NAME; maps to DISPLAY_NAME. */
    public String getVendorName() { return displayName; }

    @PrePersist
    protected void onCreate() {
        if (insertedOn == null) insertedOn = LocalDateTime.now();
        if (updatedOn == null) updatedOn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedOn = LocalDateTime.now();
    }
}
