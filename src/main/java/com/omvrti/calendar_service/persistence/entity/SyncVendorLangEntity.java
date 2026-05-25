package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "SYNC_VENDOR_LANG")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncVendorLangEntity {

    @Id
    @SequenceGenerator(name = "sync_vendor_lang_seq", sequenceName = "SYNC_VENDOR_LANG_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sync_vendor_lang_seq")
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SYNC_VENDOR_ID", nullable = false)
    private SyncVendorEntity syncVendor;

    @Column(name = "LANGUAGE_ID", nullable = false)
    private Integer languageId;

    @Column(name = "DISPLAY_NAME", length = 200)
    private String displayName;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Column(name = "LOGO", length = 2000)
    private String logo;

    @Column(name = "INSERTED_ON")
    private LocalDateTime insertedOn;

    @Column(name = "UPDATED_ON")
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
