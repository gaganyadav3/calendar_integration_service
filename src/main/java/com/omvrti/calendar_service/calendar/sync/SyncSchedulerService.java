package com.omvrti.calendar_service.calendar.sync;

import com.omvrti.calendar_service.calendar.service.WebhookManagementService;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEntity;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarWebhookEntity;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.repository.CustomerUserSyncRepository;
import com.omvrti.calendar_service.persistence.service.SyncVendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scheduled jobs for webhook lifecycle and token maintenance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncSchedulerService {

    private final WebhookManagementService webhookManagementService;
    private final TokenRefreshService tokenRefreshService;
    private final CustomerUserSyncRepository customerUserSyncRepository;
    private final SyncVendorService syncVendorService;

    // ─────────────────────────────────────────────────────────────────────────
    // Webhook renewal — every hour
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Finds webhooks whose renewal window has opened (within 24 h of expiry) and renews them.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void renewExpiringWebhooks() {
        log.debug("Webhook renewal job started");

        List<CUSyncCalendarWebhookEntity> due = webhookManagementService.getWebhooksNeedingRenewal();
        log.info("Webhooks needing renewal: {}", due.size());

        for (CUSyncCalendarWebhookEntity webhook : due) {
            try {
                renewWebhook(webhook);
            } catch (Exception e) {
                log.error("Failed to renew webhook {}: {}",
                        webhook.getExternalChannelId(), e.getMessage(), e);
            }
        }

        log.debug("Webhook renewal job completed");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token pre-refresh — every 15 minutes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pre-emptively refreshes access tokens that are expired or nearly expired
     * so that sync jobs don't hit auth errors mid-run.
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void refreshExpiringTokens() {
        log.debug("Token pre-refresh job started");

        List<CustomerUserSyncEntity> activeSyncs =
                customerUserSyncRepository.findAllActiveSyncsWithCustomerUser();

        int refreshed = 0;
        for (CustomerUserSyncEntity sync : activeSyncs) {
            if (sync.isTokenExpired()) {
                try {
                    ProviderType provider = resolveProvider(sync);
                    if (provider != null) {
                        tokenRefreshService.refreshCustomerUserSyncToken(sync, provider);
                        refreshed++;
                        log.debug("Pre-refreshed token for {} - {}", sync.getSyncEmail(), provider);
                    }
                } catch (Exception e) {
                    log.warn("Token pre-refresh failed for {} - {}: {}",
                            sync.getSyncEmail(),
                            sync.getSyncVendor().getVendorCode(),
                            e.getMessage());
                }
            }
        }

        if (refreshed > 0) {
            log.info("Pre-refreshed {} expired tokens", refreshed);
        }
        log.debug("Token pre-refresh job completed");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stale sync retry — every 30 minutes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retries syncs that are stuck in IN_PROGRESS or failed recently.
     * A sync is considered stale if its last calendar sync was > 2 hours ago.
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void retryStaleSyncs() {
        log.debug("Stale sync retry job started");

        List<CustomerUserSyncEntity> activeSyncs =
                customerUserSyncRepository.findAllActiveSyncsWithCustomerUser();

        for (CustomerUserSyncEntity sync : activeSyncs) {
            boolean isStale = sync.getLastSyncDate() == null
                    || sync.getLastSyncDate().isBefore(
                            java.time.LocalDateTime.now().minusHours(2));

            if (isStale && sync.getSyncStatus() != null
                    && "IN_PROGRESS".equals(sync.getSyncStatus().getName())) {
                log.warn("Sync appears stuck IN_PROGRESS for {} - {} (lastSync={}), will be retried by ScheduledSyncService",
                        sync.getSyncEmail(), sync.getSyncVendor().getVendorCode(), sync.getLastSyncDate());
            }
        }

        log.debug("Stale sync retry job completed");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void renewWebhook(CUSyncCalendarWebhookEntity webhook) {
        CUSyncCalendarEntity calendar = webhook.getCuSyncCalendar();
        if (calendar == null || calendar.getCustomerUserSync() == null) {
            log.warn("Webhook {} has no associated calendar/sync — skipping", webhook.getExternalChannelId());
            return;
        }

        ProviderType provider = resolveProvider(calendar.getCustomerUserSync());
        if (provider == null) {
            log.warn("Unknown vendor for webhook {} — skipping", webhook.getExternalChannelId());
            return;
        }

        log.info("Renewing {} webhook {} (expiry={})",
                provider, webhook.getExternalChannelId(), webhook.getExpiryDate());

        if (provider == ProviderType.GOOGLE) {
            webhookManagementService.renewGoogleWebhook(webhook);
        } else if (provider == ProviderType.OUTLOOK) {
            webhookManagementService.renewOutlookWebhook(webhook);
        } else {
            log.debug("Webhook renewal not supported for provider {}", provider);
        }
    }

    private ProviderType resolveProvider(CustomerUserSyncEntity sync) {
        try {
            return ProviderType.valueOf(sync.getSyncVendor().getVendorCode());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown vendor code: {}", sync.getSyncVendor().getVendorCode());
            return null;
        }
    }
}
