package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.EventGuestResponseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventGuestResponseRepository extends JpaRepository<EventGuestResponseEntity, Long> {

    Optional<EventGuestResponseEntity> findFirstByNameOrderByIdAsc(String name);

    /** Safe wrapper — uses findFirst to tolerate duplicate master-data rows. */
    default Optional<EventGuestResponseEntity> findByName(String name) {
        return findFirstByNameOrderByIdAsc(name);
    }

    default Optional<EventGuestResponseEntity> findByResponseCode(String code) {
        return findFirstByNameOrderByIdAsc(code);
    }
}
