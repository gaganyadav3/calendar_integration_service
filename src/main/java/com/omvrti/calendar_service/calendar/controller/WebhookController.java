package com.omvrti.calendar_service.calendar.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omvrti.calendar_service.calendar.service.ProviderCalendarService;
import com.omvrti.calendar_service.calendar.service.WebhookManagementService;
import com.omvrti.calendar_service.calendar.sync.SyncEngine;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEntity;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarWebhookEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Webhook endpoint receiver for Google Calendar push notifications
 * and Microsoft Graph change notifications.
 */
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookManagementService webhookManagementService;
    private final ProviderCalendarService providerCalendarService;
    private final SyncEngine syncEngine;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // Google Calendar push notification
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Google sends a POST to this endpoint when calendar events change.
     * Headers:
     *   X-Goog-Channel-ID   — the channel ID we registered
     *   X-Goog-Resource-State — "sync" (initial) or "exists" (change happened)
     *   X-Goog-Resource-ID   — resource ID of the watched resource
     *   X-Goog-Resource-URI  — full URI of the resource
     */
    @PostMapping("/google")
    public ResponseEntity<Void> handleGoogleWebhook(
            @RequestHeader(value = "X-Goog-Channel-ID", required = false) String channelId,
            @RequestHeader(value = "X-Goog-Resource-State", required = false) String resourceState,
            @RequestHeader(value = "X-Goog-Resource-ID", required = false) String resourceId,
            @RequestHeader(value = "X-Goog-Resource-URI", required = false) String resourceUri) {

        log.info("Google webhook received: channelId={} state={}", channelId, resourceState);

        if (channelId == null) {
            log.warn("Google webhook missing X-Goog-Channel-ID");
            return ResponseEntity.ok().build();
        }

        // "sync" is just the confirmation notification — ignore it
        if ("sync".equalsIgnoreCase(resourceState)) {
            log.debug("Google webhook sync confirmation for channel {}", channelId);
            return ResponseEntity.ok().build();
        }

        if (!"exists".equalsIgnoreCase(resourceState)) {
            log.debug("Google webhook unrecognized state: {}", resourceState);
            return ResponseEntity.ok().build();
        }

        try {
            triggerIncrementalSyncForChannel(channelId);
        } catch (Exception e) {
            log.error("Error processing Google webhook for channel {}: {}", channelId, e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Outlook / Microsoft Graph change notification
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Outlook sends a POST to this endpoint.
     * On first registration, Graph sends a GET/POST with a validationToken parameter
     * which must be echoed back as plain text with 200 OK.
     * Subsequent calls carry a JSON body with change notifications.
     */
    @PostMapping(value = "/outlook",
                 produces = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> handleOutlookWebhook(
            @RequestParam(required = false) String validationToken,
            @RequestBody(required = false) String body) {

        // Validation handshake
        if (validationToken != null && !validationToken.isBlank()) {
            log.info("Outlook webhook validation handshake");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(validationToken);
        }

        log.info("Outlook webhook change notification received");

        if (body != null && !body.isBlank()) {
            try {
                processOutlookNotifications(body);
            } catch (Exception e) {
                log.error("Error processing Outlook webhook: {}", e.getMessage(), e);
            }
        }

        return ResponseEntity.accepted().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Webhook registration endpoint
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Register a Google webhook for a specific calendar.
     * POST /api/webhook/register/google?calendarId=...
     */
    @PostMapping("/register/google")
    public ResponseEntity<?> registerGoogleWebhook(
            @RequestHeader("X-USER-EMAIL") String userEmail,
            @RequestParam(defaultValue = "primary") String calendarId) {
        log.info("Registering Google webhook for {} calendar {}", userEmail, calendarId);
        try {
            webhookManagementService.registerGoogleWebhook(userEmail, calendarId);
            return ResponseEntity.ok(java.util.Map.of("success", true, "message",
                    "Google webhook registered for calendar: " + calendarId));
        } catch (Exception e) {
            log.error("Failed to register Google webhook", e);
            return ResponseEntity.internalServerError()
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /**
     * Register an Outlook subscription for a specific calendar.
     * POST /api/webhook/register/outlook?calendarId=...
     */
    @PostMapping("/register/outlook")
    public ResponseEntity<?> registerOutlookWebhook(
            @RequestHeader("X-USER-EMAIL") String userEmail,
            @RequestParam(required = false) String calendarId) {
        log.info("Registering Outlook subscription for {} calendar {}", userEmail, calendarId);
        try {
            webhookManagementService.registerOutlookWebhook(userEmail, calendarId);
            return ResponseEntity.ok(java.util.Map.of("success", true, "message",
                    "Outlook webhook registered"));
        } catch (Exception e) {
            log.error("Failed to register Outlook webhook", e);
            return ResponseEntity.internalServerError()
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void triggerIncrementalSyncForChannel(String channelId) {
        Optional<CUSyncCalendarWebhookEntity> webhookOpt =
                webhookManagementService.getWebhookByChannelId(channelId);

        if (webhookOpt.isEmpty()) {
            log.warn("No webhook found for channelId: {}", channelId);
            return;
        }

        CUSyncCalendarWebhookEntity webhook = webhookOpt.get();
        if (!Integer.valueOf(1).equals(webhook.getIsActive()) || webhook.isExpired()) {
            log.debug("Ignoring inactive/expired webhook for channel {}", channelId);
            return;
        }
        CUSyncCalendarEntity calendar = webhook.getCuSyncCalendar();
        var sync = calendar.getCustomerUserSync();

        log.info("Triggering incremental sync for {} calendar {} via webhook",
                sync.getSyncEmail(), calendar.getCalendarReference());

        syncEngine.triggerIncrementalSync(sync, calendar);
    }

    private void processOutlookNotifications(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode notifications = root.get("value");
        if (notifications == null || !notifications.isArray()) return;

        for (JsonNode notification : notifications) {
            String subscriptionId = notification.path("subscriptionId").asText(null);
            String changeType = notification.path("changeType").asText(null);
            String clientState = notification.path("clientState").asText(null);

            log.info("Outlook notification: subscriptionId={} changeType={}", subscriptionId, changeType);

            if (subscriptionId != null) {
                triggerIncrementalSyncForSubscription(subscriptionId);
            }
        }
    }

    private void triggerIncrementalSyncForSubscription(String subscriptionId) {
        webhookManagementService.getWebhookBySubscriptionId(subscriptionId).ifPresent(webhook -> {
            CUSyncCalendarEntity calendar = webhook.getCuSyncCalendar();
            var sync = calendar.getCustomerUserSync();
            log.info("Triggering incremental sync for {} calendar {} via Outlook subscription",
                    sync.getSyncEmail(), calendar.getCalendarReference());
            syncEngine.triggerIncrementalSync(sync, calendar);
        });
    }
}
