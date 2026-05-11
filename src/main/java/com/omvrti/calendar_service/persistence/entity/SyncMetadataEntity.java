package com.omvrti.calendar_service.persistence.entity;

import com.omvrti.calendar_service.common.enums.ProviderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Tracks sync metadata to avoid re-syncing same events
 */
@Entity
@Table(name = "sync_metadata", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "provider"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private ProviderType provider;

    @Column
    private LocalDateTime lastSyncTime;

    @Column
    private String lastSyncToken;

    @Column
    private Integer lastSyncCount;

    @Column
    private String lastSyncStatus;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private String lastErrorMessage;

    @Column
    private Integer consecutiveFailures;
}

