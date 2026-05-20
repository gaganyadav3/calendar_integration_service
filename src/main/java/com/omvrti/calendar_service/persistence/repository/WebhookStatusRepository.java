package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.WebhookStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookStatusRepository extends JpaRepository<WebhookStatusEntity, Long> {

    Optional<WebhookStatusEntity> findFirstByNameOrderByIdAsc(String name);

    /** Safe wrapper — uses findFirst to tolerate duplicate master-data rows. */
    default Optional<WebhookStatusEntity> findByName(String name) {
        return findFirstByNameOrderByIdAsc(name);
    }

    /** Backward-compat — old column was WEBHOOK_STATUS_CODE, now NAME. */
    default Optional<WebhookStatusEntity> findByWebhookStatusCode(String webhookStatusCode) {
        return findFirstByNameOrderByIdAsc(webhookStatusCode);
    }
}
