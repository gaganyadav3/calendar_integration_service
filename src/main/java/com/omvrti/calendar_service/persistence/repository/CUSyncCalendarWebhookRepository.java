package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEntity;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarWebhookEntity;
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
}
