package com.omvrti.calendar_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "CUSTOMER_USER")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerUserEntity {

    @Id
    @SequenceGenerator(name = "customer_user_seq", sequenceName = "CUSTOMER_USER_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_user_seq")
    @Column(name = "ID")
    private Long id;
    @Column(name = "CUSTOMER_ID")
    private Long customerId;

    @Column(name = "EMAIL", length = 100)
    private String email;

    @Column(name = "FIRST_NAME", nullable = false, length = 100)
    private String firstName;

    @Column(name = "MIDDLE_NAME", length = 100)
    private String middleName;

    // IS_ACTIVE does not exist in CUSTOMER_USER table — kept as transient for backward compat
    @Transient
    private Integer isActive;

    @Column(name = "INSERTED_ON")
    private LocalDateTime insertedOn;

    @Column(name = "UPDATED_ON")
    private LocalDateTime updatedOn;

    @PrePersist
    protected void onCreate() {
        if (insertedOn == null) insertedOn = LocalDateTime.now();
        if (updatedOn == null) updatedOn = LocalDateTime.now();
        if (firstName == null) firstName = "";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedOn = LocalDateTime.now();
    }
}
