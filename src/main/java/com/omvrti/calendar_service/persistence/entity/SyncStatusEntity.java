package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "SYNC_STATUS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncStatusEntity {

    @Id
    @SequenceGenerator(name = "sync_status_seq", sequenceName = "SYNC_STATUS_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sync_status_seq")
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = 30)
    private String name;

    @Column(name = "DESCRIPTION", nullable = false, length = 100)
    private String description;

    @Column(name = "IS_CONNECTED", nullable = false)
    private Integer isConnected;

    @Column(name = "IS_EXPIRED", nullable = false)
    private Integer isExpired;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Integer isActive;

    // DISPLAY_SORT_ORDER does not exist in DB — kept as transient for MasterDataInitializationService
    @Transient
    private Integer displaySortOrder;

    @Column(name = "INSERTED_ON", nullable = false)
    private LocalDateTime insertedOn;

    @Column(name = "UPDATED_ON", nullable = false)
    private LocalDateTime updatedOn;

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
