package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.OMEventEntity;
import com.omvrti.calendar_service.persistence.entity.OMEventGuestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OMEventGuestRepository extends JpaRepository<OMEventGuestEntity, Long> {

    List<OMEventGuestEntity> findByOmEvent(OMEventEntity omEvent);

    List<OMEventGuestEntity> findByOmEventAndIsOrganizer(OMEventEntity omEvent, Integer isOrganizer);

    void deleteByOmEvent(OMEventEntity omEvent);
}
