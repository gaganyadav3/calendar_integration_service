package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "EVENT_GUEST_RESPONSE")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventGuestResponseEntity {

    @Id
    @SequenceGenerator(name = "event_guest_resp_seq", sequenceName = "EVENT_GUEST_RESPONSE_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "event_guest_resp_seq")
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = 30)
    private String name;

    @Column(name = "DESCRIPTION", nullable = false, length = 100)
    private String description;

    @Column(name = "IS_ACCEPTED", nullable = false)
    private Integer isAccepted;

    @Column(name = "IS_DENIED", nullable = false)
    private Integer isDenied;

    @Column(name = "IS_UNSURE", nullable = false)
    private Integer isUnsure;

    @Column(name = "IS_PENDING", nullable = false)
    private Integer isPending;

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
