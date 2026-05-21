package com.omvrti.calendar_service.persistence.service;

import com.omvrti.calendar_service.persistence.entity.*;
import com.omvrti.calendar_service.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MasterDataInitializationService {

    private final SyncVendorRepository syncVendorRepository;
    private final SyncStatusRepository syncStatusRepository;
    private final CalendarEventStatusRepository eventStatusRepository;
    private final EventGuestResponseRepository guestResponseRepository;
    private final WebhookStatusRepository webhookStatusRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeMasterData() {
        log.info("Initializing master data for Oracle schema");
        initializeSyncVendors();
        initializeSyncStatuses();
        initializeCalendarEventStatuses();
        initializeEventGuestResponses();
        initializeWebhookStatuses();
        verifyMasterData();
        log.info("Master data initialization completed");
    }

    // ── Verification — logs row counts so any insert failure is immediately visible ──

    private void verifyMasterData() {
        long vendors   = syncVendorRepository.count();
        long statuses  = syncStatusRepository.count();
        long evtStatus = eventStatusRepository.count();
        long guestResp = guestResponseRepository.count();
        long wbStatus  = webhookStatusRepository.count();
        log.info("=== STARTUP MASTER DATA VALIDATION ===");
        log.info("  SYNC_VENDOR:{}  SYNC_STATUS:{}  CAL_EVENT_STATUS:{}  GUEST_RESPONSE:{}  WEBHOOK_STATUS:{}",
                vendors, statuses, evtStatus, guestResp, wbStatus);

        if (vendors == 0)
            log.warn("[STARTUP] SYNC_VENDOR is EMPTY — OAuth provider lookups will throw on first request.");
        if (statuses == 0)
            log.warn("[STARTUP] SYNC_STATUS is EMPTY — token saves will fail with IllegalStateException. " +
                     "Check Oracle schema for unmapped NOT NULL columns in SYNC_STATUS.");
        if (evtStatus == 0)
            log.warn("[STARTUP] CALENDAR_EVENT_STATUS is EMPTY — event syncs will store null CALENDAR_EVENT_STATUS_ID " +
                     "which may violate a NOT NULL DB constraint.");
        if (guestResp == 0)
            log.warn("[STARTUP] EVENT_GUEST_RESPONSE is EMPTY — attendee persistence will store null EVENT_GUEST_RESPONSE_ID.");
        if (wbStatus == 0)
            log.warn("[STARTUP] WEBHOOK_STATUS is EMPTY — webhook registration will fail.");

        syncStatusRepository.findAll().forEach(s ->
                log.debug("  SYNC_STATUS row: id={} name={} isConnected={} isActive={}",
                        s.getId(), s.getName(), s.getIsConnected(), s.getIsActive()));
        syncVendorRepository.findAll().forEach(v ->
                log.debug("  SYNC_VENDOR row: id={} name={} displayName={}", v.getId(), v.getName(), v.getDisplayName()));
        eventStatusRepository.findAll().forEach(es ->
                log.debug("  CAL_EVENT_STATUS row: id={} name={} isCancelled={}", es.getId(), es.getName(), es.getIsCancelled()));
        log.info("=== STARTUP VALIDATION COMPLETE ===");
    }

    // ── Sync vendors ──────────────────────────────────────────────────────────

    private void initializeSyncVendors() {
        createVendorIfNotExists("GOOGLE",  "Google Calendar",    1);
        createVendorIfNotExists("OUTLOOK", "Microsoft Outlook",  2);
    }

    private void createVendorIfNotExists(String name, String displayName, int sortOrder) {
        if (syncVendorRepository.existsByName(name)) {
            log.debug("Sync vendor {} already exists — skipping", name);
            return;
        }
        try {
            syncVendorRepository.saveAndFlush(SyncVendorEntity.builder()
                    .name(name)
                    .displayName(displayName)
                    .apiAuthType(1)
                    .vendorType(1)
                    .isNewConnection(0)
                    .isValid(1)
                    .displaySortOrder(sortOrder)
                    .build());
            log.debug("Created sync vendor: {}", name);
        } catch (DataIntegrityViolationException e) {
            log.warn("Could not insert sync vendor '{}' — {}", name, rootMessage(e));
        }
    }

    // ── Sync statuses ─────────────────────────────────────────────────────────

    private void initializeSyncStatuses() {
        createSyncStatusIfNotExists("PENDING",      "Pending",     "Sync is pending",             0, 0, 0, 0);
        createSyncStatusIfNotExists("IN_PROGRESS",  "In Progress", "Sync is in progress",         1, 0, 1, 1);
        createSyncStatusIfNotExists("SUCCESS",      "Success",     "Sync completed successfully", 1, 0, 1, 2);
        createSyncStatusIfNotExists("FAILED",       "Failed",      "Sync failed",                 0, 0, 0, 3);
        createSyncStatusIfNotExists("PARTIAL",      "Partial",     "Sync completed with errors",  1, 0, 1, 4);
    }

    private void createSyncStatusIfNotExists(String name, String displayName, String description,
                                             int isConnected, int isExpired, int isActive, int sortOrder) {
        if (syncStatusRepository.findByStatusCode(name).isPresent()) {
            log.debug("Sync status {} already exists — skipping", name);
            return;
        }
        try {
            syncStatusRepository.saveAndFlush(SyncStatusEntity.builder()
                    .name(name)
                    .description(description)
                    .isConnected(isConnected)
                    .isExpired(isExpired)
                    .isActive(isActive)
                    .displaySortOrder(sortOrder)
                    .build());
            log.debug("Created sync status: {}", name);
        } catch (DataIntegrityViolationException e) {
            log.warn("Could not insert sync status '{}' — {}. " +
                     "If Oracle SYNC_STATUS has unmapped NOT NULL columns (e.g. STATUS_CODE), add them to SyncStatusEntity.",
                     name, rootMessage(e));
        }
    }

    // ── Calendar event statuses ───────────────────────────────────────────────

    private void initializeCalendarEventStatuses() {
        createEventStatusIfNotExists("CONFIRMED",  "Event is confirmed", 1, 0);
        createEventStatusIfNotExists("TENTATIVE",  "Event is tentative", 1, 0);
        createEventStatusIfNotExists("CANCELLED",  "Event is cancelled", 0, 1);
    }

    private void createEventStatusIfNotExists(String name, String description, int isActive, int isCancelled) {
        if (eventStatusRepository.findByEventStatusCode(name).isPresent()) {
            log.debug("Calendar event status {} already exists — skipping", name);
            return;
        }
        try {
            eventStatusRepository.saveAndFlush(CalendarEventStatusEntity.builder()
                    .name(name)
                    .description(description)
                    .isActive(isActive)
                    .isCancelled(isCancelled)
                    .build());
            log.debug("Created calendar event status: {}", name);
        } catch (DataIntegrityViolationException e) {
            log.warn("Could not insert calendar event status '{}' — {}", name, rootMessage(e));
        }
    }

    // ── Event guest responses ─────────────────────────────────────────────────

    private void initializeEventGuestResponses() {
        createGuestResponseIfNotExists("ACCEPTED",     "Accepted",     1, 0, 0, 0);
        createGuestResponseIfNotExists("DECLINED",     "Declined",     0, 1, 0, 0);
        createGuestResponseIfNotExists("TENTATIVE",    "Tentative",    0, 0, 1, 0);
        createGuestResponseIfNotExists("NEEDS_ACTION", "Needs Action", 0, 0, 0, 1);
    }

    private void createGuestResponseIfNotExists(String name, String description,
                                                int isAccepted, int isDenied, int isUnsure, int isPending) {
        if (guestResponseRepository.findByName(name).isPresent()) {
            log.debug("Guest response {} already exists — skipping", name);
            return;
        }
        try {
            guestResponseRepository.saveAndFlush(EventGuestResponseEntity.builder()
                    .name(name)
                    .description(description)
                    .isAccepted(isAccepted)
                    .isDenied(isDenied)
                    .isUnsure(isUnsure)
                    .isPending(isPending)
                    .build());
            log.debug("Created guest response: {}", name);
        } catch (DataIntegrityViolationException e) {
            log.warn("Could not insert guest response '{}' — {}", name, rootMessage(e));
        }
    }

    // ── Webhook statuses ──────────────────────────────────────────────────────

    private void initializeWebhookStatuses() {
        createWebhookStatusIfNotExists("ACTIVE",   "Webhook is active",          1, 0);
        createWebhookStatusIfNotExists("EXPIRED",  "Webhook has expired",        0, 1);
        createWebhookStatusIfNotExists("FAILED",   "Webhook failed",             0, 0);
        createWebhookStatusIfNotExists("PENDING",  "Webhook pending activation", 0, 0);
    }

    private void createWebhookStatusIfNotExists(String name, String description, int isActive, int isExpired) {
        if (webhookStatusRepository.findByWebhookStatusCode(name).isPresent()) {
            log.debug("Webhook status {} already exists — skipping", name);
            return;
        }
        try {
            webhookStatusRepository.saveAndFlush(WebhookStatusEntity.builder()
                    .name(name)
                    .description(description)
                    .isActive(isActive)
                    .isExpired(isExpired)
                    .build());
            log.debug("Created webhook status: {}", name);
        } catch (DataIntegrityViolationException e) {
            log.warn("Could not insert webhook status '{}' — {}", name, rootMessage(e));
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }
}
