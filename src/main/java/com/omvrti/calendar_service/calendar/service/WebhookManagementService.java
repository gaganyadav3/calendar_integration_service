package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEntity;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarWebhookEntity;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.repository.CUSyncCalendarWebhookRepository;
import com.omvrti.calendar_service.persistence.repository.WebhookStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookManagementService {

    private final CUSyncCalendarWebhookRepository webhookRepository;
    private final WebhookStatusRepository webhookStatusRepository;
    private final CustomerUserSyncService customerUserSyncService;
    private final ProviderCalendarService providerCalendarService;
    private final TokenRefreshService tokenRefreshService;
    private final Map<ProviderType, ICalendarProvider> calendarProviders;

    @Value("${webhook.callback.base-url}")
    private String callbackBaseUrl;

    // ── Registration ──────────────────────────────────────────────────────────

    @Transactional
    public void registerGoogleWebhook(String userEmail, String calendarId) {
        log.info("Registering Google webhook for {} calendar {}", userEmail, calendarId);
        CustomerUserSyncEntity sync = requireSync(userEmail, ProviderType.GOOGLE);
        tokenRefreshService.getValidAccessToken(sync, ProviderType.GOOGLE);
        CUSyncCalendarEntity calendar = providerCalendarService.getOrCreatePrimaryCalendar(sync, calendarId);

        ICalendarProvider.WebhookInfo info =
                calendarProviders.get(ProviderType.GOOGLE)
                        .registerWebhook(sync, calendar.getCalendarReference(),
                                callbackBaseUrl + "/api/webhook/google");
        if (info != null) {
            createOrUpdateWebhook(calendar, info.channelId(), null, null, info.expiryDate(), "ACTIVE");
            log.info("Google webhook registered: channelId={}", info.channelId());
        }
    }

    @Transactional
    public void registerOutlookWebhook(String userEmail, String calendarId) {
        log.info("Registering Outlook webhook for {} calendar {}", userEmail, calendarId);
        CustomerUserSyncEntity sync = requireSync(userEmail, ProviderType.OUTLOOK);
        tokenRefreshService.getValidAccessToken(sync, ProviderType.OUTLOOK);
        CUSyncCalendarEntity calendar = providerCalendarService.getOrCreatePrimaryCalendar(sync, calendarId);

        ICalendarProvider.WebhookInfo info =
                calendarProviders.get(ProviderType.OUTLOOK)
                        .registerWebhook(sync, calendar.getCalendarReference(),
                                callbackBaseUrl + "/api/webhook/outlook");
        if (info != null) {
            createOrUpdateWebhook(calendar, info.channelId(), null, info.channelId(), info.expiryDate(), "ACTIVE");
            log.info("Outlook webhook registered: subscriptionId={}", info.channelId());
        }
    }

    // ── Renewal ───────────────────────────────────────────────────────────────

    @Transactional
    public void renewGoogleWebhook(CUSyncCalendarWebhookEntity webhook) {
        CUSyncCalendarEntity calendar = webhook.getCuSyncCalendar();
        CustomerUserSyncEntity sync = calendar.getCustomerUserSync();
        tokenRefreshService.getValidAccessToken(sync, ProviderType.GOOGLE);

        ICalendarProvider.WebhookInfo info =
                calendarProviders.get(ProviderType.GOOGLE)
                        .renewWebhook(sync, webhook.getExternalChannelId(),
                                calendar.getCalendarReference(), callbackBaseUrl + "/api/webhook/google");
        if (info != null) {
            deactivateWebhook(webhook, "EXPIRED");
            createOrUpdateWebhook(calendar, info.channelId(), null, null, info.expiryDate(), "ACTIVE");
            log.info("Google webhook renewed: new channelId={}", info.channelId());
        }
    }

    @Transactional
    public void renewOutlookWebhook(CUSyncCalendarWebhookEntity webhook) {
        CUSyncCalendarEntity calendar = webhook.getCuSyncCalendar();
        CustomerUserSyncEntity sync = calendar.getCustomerUserSync();
        tokenRefreshService.getValidAccessToken(sync, ProviderType.OUTLOOK);

        ICalendarProvider.WebhookInfo info =
                calendarProviders.get(ProviderType.OUTLOOK)
                        .renewWebhook(sync, webhook.getSubscriptionId(),
                                calendar.getCalendarReference(), callbackBaseUrl + "/api/webhook/outlook");
        if (info != null) {
            updateWebhookExpiry(webhook, info.expiryDate());
            log.info("Outlook webhook renewed: new expiry={}", info.expiryDate());
        }
    }

    // ── CRUD / lookups ────────────────────────────────────────────────────────

    @Transactional
    public CUSyncCalendarWebhookEntity createOrUpdateWebhook(
            CUSyncCalendarEntity calendar, String externalChannelId,
            String resourceUrl, String subscriptionId,
            OffsetDateTime expiryDate, String statusCode) {

        Optional<CUSyncCalendarWebhookEntity> existingOpt =
                webhookRepository.findByExternalChannelId(externalChannelId);

        CUSyncCalendarWebhookEntity webhook;
        if (existingOpt.isPresent()) {
            webhook = existingOpt.get();
        } else {
            webhookRepository.findByCuSyncCalendarAndIsActive(calendar, 1)
                    .forEach(w -> deactivateWebhook(w, "EXPIRED"));
            webhook = CUSyncCalendarWebhookEntity.builder()
                    .cuSyncCalendar(calendar)
                    .externalChannelId(externalChannelId)
                    .expiryDate(expiryDate != null ? expiryDate.toLocalDateTime()
                            : java.time.LocalDateTime.now().plusDays(7))
                    .build();
        }

        webhook.setResourceUrl(resourceUrl);
        webhook.setSubscriptionId(subscriptionId);
        if (expiryDate != null) webhook.setExpiryDate(expiryDate.toLocalDateTime());
        if (statusCode != null) {
            webhookStatusRepository.findByWebhookStatusCode(statusCode)
                    .ifPresent(webhook::setWebhookStatus);
        }
        webhook.setIsActive("ACTIVE".equalsIgnoreCase(statusCode) ? 1 : 0);
        if (Integer.valueOf(1).equals(webhook.getIsActive())) {
            webhook.setIsDeleted(0);
            webhook.setDeletedOn(null);
        }
        return webhookRepository.save(webhook);
    }

    public Optional<CUSyncCalendarWebhookEntity> getWebhookByChannelId(String externalChannelId) {
        return webhookRepository.findByExternalChannelId(externalChannelId);
    }

    public Optional<CUSyncCalendarWebhookEntity> getWebhookBySubscriptionId(String subscriptionId) {
        return webhookRepository.findBySubscriptionId(subscriptionId).stream().findFirst();
    }

    public List<CUSyncCalendarWebhookEntity> getWebhooks(CUSyncCalendarEntity calendar) {
        return webhookRepository.findByCuSyncCalendar(calendar);
    }

    public boolean isWebhookExpired(CUSyncCalendarWebhookEntity webhook) {
        return webhook.isExpired();
    }

    public List<CUSyncCalendarWebhookEntity> getExpiredWebhooks() {
        return webhookRepository.findAllEager().stream()
                .filter(CUSyncCalendarWebhookEntity::isExpired).toList();
    }

    public List<CUSyncCalendarWebhookEntity> getWebhooksNeedingRenewal() {
        return webhookRepository.findAllEager().stream()
                .filter(CUSyncCalendarWebhookEntity::needsRenewal).toList();
    }

    @Transactional
    public void updateWebhookExpiry(CUSyncCalendarWebhookEntity webhook, OffsetDateTime expiryDate) {
        if (expiryDate != null) webhook.setExpiryDate(expiryDate.toLocalDateTime());
        webhookRepository.save(webhook);
    }

    @Transactional
    public void deleteWebhook(CUSyncCalendarWebhookEntity webhook) {
        deactivateWebhook(webhook, "INACTIVE");
    }

    @Transactional
    public void updateWebhookStatus(CUSyncCalendarWebhookEntity webhook, String statusCode) {
        webhookStatusRepository.findByWebhookStatusCode(statusCode).ifPresent(webhook::setWebhookStatus);
        webhook.setIsActive("ACTIVE".equalsIgnoreCase(statusCode) ? 1 : 0);
        webhookRepository.save(webhook);
    }

    private void deactivateWebhook(CUSyncCalendarWebhookEntity webhook, String statusCode) {
        webhook.setIsActive(0);
        webhook.setIsDeleted(1);
        webhook.setDeletedOn(LocalDateTime.now());
        webhookStatusRepository.findByWebhookStatusCode(statusCode).ifPresent(webhook::setWebhookStatus);
        webhookRepository.save(webhook);
    }

    private CustomerUserSyncEntity requireSync(String userEmail, ProviderType provider) {
        return customerUserSyncService.getSyncByEmail(userEmail, provider)
                .filter(s -> Integer.valueOf(1).equals(s.getIsActive()))
                .orElseThrow(() -> new IllegalStateException(
                        provider + " not connected for user: " + userEmail));
    }
}
