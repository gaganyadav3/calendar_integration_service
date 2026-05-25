package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEntity;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarWebhookEntity;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CUSyncCalendarWebhookRepository extends JpaRepository<CUSyncCalendarWebhookEntity, Long> {

    List<CUSyncCalendarWebhookEntity> findByCuSyncCalendar(CUSyncCalendarEntity cuSyncCalendar);

    /**
     * Eagerly loads the full sync chain so webhook controllers never hit LazyInitializationException
     * when accessing webhook.getCuSyncCalendar().getCustomerUserSync().getSyncVendor() etc.
     */
    @Query("SELECT w FROM CUSyncCalendarWebhookEntity w " +
           "JOIN FETCH w.cuSyncCalendar c " +
           "JOIN FETCH c.customerUserSync s " +
           "JOIN FETCH s.syncVendor " +
           "JOIN FETCH s.syncStatus " +
           "LEFT JOIN FETCH w.webhookStatus " +
           "WHERE w.externalChannelId = :channelId")
    Optional<CUSyncCalendarWebhookEntity> findByExternalChannelId(@Param("channelId") String channelId);

    /** subscriptionId is stored in EXTERNAL_CHANNEL_ID for Outlook webhooks. */
    default List<CUSyncCalendarWebhookEntity> findBySubscriptionId(String subscriptionId) {
        return findByExternalChannelId(subscriptionId)
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * Loads all webhooks with their full calendar + sync chain eagerly.
     * Used by the scheduler so that getCuSyncCalendar() / getCustomerUserSync() / getSyncVendor()
     * are safe to access after the Hibernate session closes (entities are detached but initialized).
     */
    @Query("SELECT w FROM CUSyncCalendarWebhookEntity w " +
           "JOIN FETCH w.cuSyncCalendar c " +
           "JOIN FETCH c.customerUserSync s " +
           "JOIN FETCH s.syncVendor " +
           "JOIN FETCH s.syncStatus " +
           "LEFT JOIN FETCH w.webhookStatus")
    List<CUSyncCalendarWebhookEntity> findAllEager();

    /**
     * Count active webhooks (WEBHOOK_STATUS.IS_ACTIVE=1) across all calendars
     * belonging to the given sync — used by the vendor status API.
     */
    @Query("SELECT COUNT(w) FROM CUSyncCalendarWebhookEntity w " +
           "WHERE w.cuSyncCalendar.customerUserSync = :sync " +
           "AND w.webhookStatus IS NOT NULL AND w.webhookStatus.isActive = 1")
    long countActiveWebhooksBySync(@Param("sync") CustomerUserSyncEntity sync);
}
