package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.CustomerUserEntity;
import com.omvrti.calendar_service.persistence.entity.OMEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OMEventRepository extends JpaRepository<OMEventEntity, Long> {

    List<OMEventEntity> findByCustomer(CustomerUserEntity customer);

    Page<OMEventEntity> findByCustomerOrderByEventStartTimeDesc(
        CustomerUserEntity customer,
        Pageable pageable
    );

    List<OMEventEntity> findByCustomerAndEventStartTimeBetween(
        CustomerUserEntity customer,
        LocalDateTime startTime,
        LocalDateTime endTime
    );

    @Query("SELECT e FROM OMEventEntity e WHERE e.customer = :customer " +
           "AND (e.eventEndTime IS NULL OR e.eventEndTime > CURRENT_TIMESTAMP)")
    List<OMEventEntity> findUpcomingEvents(@Param("customer") CustomerUserEntity customer);
}
