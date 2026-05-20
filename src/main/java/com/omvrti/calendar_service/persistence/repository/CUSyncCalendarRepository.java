package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEntity;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CUSyncCalendarRepository extends JpaRepository<CUSyncCalendarEntity, Long> {

    List<CUSyncCalendarEntity> findByCustomerUserSync(CustomerUserSyncEntity customerUserSync);

    Optional<CUSyncCalendarEntity> findByCustomerUserSyncAndCalendarReference(
            CustomerUserSyncEntity customerUserSync, String calendarReference);

    @Query("SELECT c FROM CUSyncCalendarEntity c WHERE c.customerUserSync = :sync AND c.isEnabled = 1")
    List<CUSyncCalendarEntity> findEnabledByCustomerUserSync(@Param("sync") CustomerUserSyncEntity sync);

    @Query("SELECT c FROM CUSyncCalendarEntity c WHERE c.customerUserSync = :sync AND c.isPrimary = 1")
    List<CUSyncCalendarEntity> findPrimaryByCustomerUserSync(@Param("sync") CustomerUserSyncEntity sync);

    /** Backward-compat alias for IS_ENABLED=1 (was IS_SYNC_ON). */
    default List<CUSyncCalendarEntity> findByCustomerUserSyncAndIsSyncOnTrue(CustomerUserSyncEntity sync) {
        return findEnabledByCustomerUserSync(sync);
    }

    /** Backward-compat alias for IS_PRIMARY=1. */
    default List<CUSyncCalendarEntity> findByCustomerUserSyncAndIsPrimaryTrue(CustomerUserSyncEntity sync) {
        return findPrimaryByCustomerUserSync(sync);
    }
}
