package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps to OM_EVENT_GUEST Oracle table.
 * IS_ORGANISER uses British spelling as in the DB column.
 */
@Entity
@Table(name = "OM_EVENT_GUEST", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"OM_EVENT_ID", "GUEST_EMAIL"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OMEventGuestEntity {

    @Id
    @SequenceGenerator(name = "om_event_guest_seq", sequenceName = "OM_EVENT_GUEST_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "om_event_guest_seq")
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "OM_EVENT_ID", nullable = false)
    private OMEventEntity omEvent;

    @Column(name = "GUEST_EMAIL", length = 1000)
    private String guestEmail;

    @Column(name = "GUEST_NAME", length = 255)
    private String guestName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EVENT_GUEST_RESPONSE_ID")
    private EventGuestResponseEntity guestResponse;

    @Column(name = "IS_OPTIONAL")
    private Integer isOptional;

    @Column(name = "IS_ORGANISER")
    private Integer isOrganizer;

    @Column(name = "IS_HUMAN")
    private Integer isHuman;

    @Column(name = "COMMENT_TEXT", length = 4000)
    private String commentText;

    @Column(name = "INSERTED_ON")
    private LocalDateTime insertedOn;

    @Column(name = "UPDATED_ON")
    private LocalDateTime updatedOn;

    @PrePersist
    protected void onCreate() {
        if (insertedOn == null) insertedOn = LocalDateTime.now();
        if (updatedOn == null) updatedOn = LocalDateTime.now();
        if (isOptional == null) isOptional = 0;
        if (isOrganizer == null) isOrganizer = 0;
        if (isHuman == null) isHuman = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedOn = LocalDateTime.now();
    }
}
