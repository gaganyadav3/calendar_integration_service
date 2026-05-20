package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "CALENDAR_EVENT_STATUS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEventStatusEntity {

    @Id
    @SequenceGenerator(name = "cal_event_status_seq", sequenceName = "CALENDAR_EVENT_STATUS_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cal_event_status_seq")
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = 30)
    private String name;

    @Column(name = "DESCRIPTION", nullable = false, length = 100)
    private String description;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Integer isActive;

    @Column(name = "IS_CANCELLED", nullable = false)
    private Integer isCancelled;

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
