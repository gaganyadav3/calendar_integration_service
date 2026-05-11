package com.omvrti.calendar_service.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.omvrti.calendar_service.common.enums.ProviderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "CALENDAR_EVENTS")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class
CalendarEventEntity {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String userEmail;
    
    @Column
    private ProviderType provider;
    
    @Column
    private String externalId;
    
    private String summary;
    private String description;
    private String location;
    
    @Column(name = "START_DATE_TIME")
    private OffsetDateTime startDateTime;
    
    @Column(name = "END_DATE_TIME")
    private OffsetDateTime endDateTime;
    
    @Column(name = "START_DATE")
    private LocalDate startDate;
    
    @Column(name = "END_DATE")
    private LocalDate endDate;
    
    private String status;
    private String organizer;
    
    @Column(name = "IS_ALL_DAY", nullable = false)
    private boolean allDay = false;
    
    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;
    
    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}

