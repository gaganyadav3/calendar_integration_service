package com.omvrti.calendar_service.persistence.entity;

import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Unified event model storing both internal and external event mappings
 */
@Entity
@Table(name = "events", indexes = {
    @Index(columnList = "user_id"),
    @Index(columnList = "provider"),
    @Index(columnList = "start_time"),
    @Index(columnList = "end_time"),
    @Index(columnList = "source"),
    @Index(columnList = "is_deleted")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private ConnectedAccountEntity connectedAccount;
    
    @Column(nullable = false, unique = true)
    private String internalId;
    
    @Column
    private String externalId;
    
    @Column
    private ProviderType provider;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventSource source;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column
    private String location;
    
    @Column(nullable = false)
    private OffsetDateTime startTime;
    
    @Column(nullable = false)
    private OffsetDateTime endTime;
    
    @Column
    private String timeZoneId;
    
    @Column
    private boolean allDay;
    
    @Column
    private String status;
    
    @Column
    private String organizer;
    
    @Column
    private boolean isCancelled;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
    
    @Column
    private OffsetDateTime externalUpdatedAt;
    
    @Column(nullable = false)
    private boolean isDeleted;
    
    @Column
    private String syncStatus;
    
    @Column(nullable = false)
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (version == null) {
            version = 0L;
        }
    }
    
    public boolean isUpcoming() {
        return endTime.isAfter(OffsetDateTime.now());
    }
    
    public boolean isBooking() {
        return !isCancelled && isUpcoming();
    }
}

