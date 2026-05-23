package com.omvrti.calendar_service.calendar.sync;

import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.calendar.service.ProviderCalendarService;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.CalendarException;
import com.omvrti.calendar_service.common.exception.SyncException;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.*;
import com.omvrti.calendar_service.persistence.repository.CustomerUserRepository;
import com.omvrti.calendar_service.persistence.repository.CustomerUserSyncRepository;
import com.omvrti.calendar_service.persistence.service.SyncVendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncEngine {

    private final CustomerUserRepository customerUserRepository;
    private final CustomerUserSyncRepository customerUserSyncRepository;
    private final ProviderCalendarService providerCalendarService;
    private final SyncVendorService syncVendorService;
    private final SyncStatusPersistenceService syncStatusPersistenceService;
    private final EventMergePersistenceService eventMergePersistenceService;
    private final TokenRefreshService tokenRefreshService;
    private final Map<ProviderType, ICalendarProvider> calendarProviders;

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Full sync for one user + provider.
     *
     * NOT @Transactional on this method intentionally:
     * - Status updates use REQUIRES_NEW (SyncStatusPersistenceService) and commit independently.
     * - Each event merge uses REQUIRES_NEW (EventMergePersistenceService) so one bad event
     *   never rolls back others.
     * - Lazy loading is avoided by using the JOIN FETCH query for the sync entity.
     */
    public SyncResult sync(String userEmail, ProviderType provider) {
        log.info("Starting sync for {} - {}", userEmail, provider);

        SyncResult result = new SyncResult();
        result.setProvider(provider);
        result.setStartTime(LocalDateTime.now());

        CustomerUserEntity customerUser = customerUserRepository.findByEmail(userEmail)
                .orElseThrow(() -> new SyncException("USER_NOT_FOUND",
                        "Customer user not found: " + userEmail));

        SyncVendorEntity vendor = syncVendorService.getOrCreateVendor(provider);

        // Use the JOIN FETCH query so syncStatus + syncVendor are eagerly loaded (no lazy proxies).
        CustomerUserSyncEntity sync = customerUserSyncRepository
                .findByCustomerUserIdAndSyncVendorId(customerUser.getId(), vendor.getId())
                .orElseThrow(() -> new SyncException("ACCOUNT_NOT_CONNECTED",
                        "Provider not connected: " + provider));

        if (!Integer.valueOf(1).equals(sync.getIsActive())) {
            throw new SyncException("ACCOUNT_INACTIVE", "Provider sync is inactive: " + provider);
        }

        ICalendarProvider calendarProvider = calendarProviders.get(provider);
        if (calendarProvider == null) {
            throw new SyncException("PROVIDER_NOT_FOUND", "Calendar provider not registered: " + provider);
        }

        // Mark IN_PROGRESS in its own committed transaction (not rolled back on failure)
        syncStatusPersistenceService.markInProgress(sync.getId());

        try {
            // Refresh token if expired before making any provider API calls
            try {
                String validToken = tokenRefreshService.getValidAccessToken(sync, provider);
                sync.setAccessToken(validToken);
            } catch (Exception e) {
                syncStatusPersistenceService.markFailed(sync.getId(), "Token refresh failed: " + e.getMessage());
                throw new SyncException("AUTH_FAILED", "Token refresh failed for " + userEmail + ": " + e.getMessage(), e);
            }

            fetchAndSaveCalendars(sync, calendarProvider);

            List<CUSyncCalendarEntity> enabledCalendars =
                    providerCalendarService.getEnabledCalendars(sync);

            for (CUSyncCalendarEntity calendar : enabledCalendars) {
                try {
                    syncCalendarEvents(sync, calendar, calendarProvider, result);
                } catch (Exception e) {
                    log.error("Failed to sync calendar {} for {}: {}",
                            calendar.getCalendarReference(), userEmail, e.getMessage(), e);
                    result.getFailedCalendarIds().add(calendar.getCalendarReference());
                }
            }

            // Mark SUCCESS — committed immediately, independently of any outer tx
            syncStatusPersistenceService.markSuccess(sync.getId());
            result.setStatus("SUCCESS");
            result.setEndTime(LocalDateTime.now());
            log.info("Sync completed for {} - {}: fetched={} synced={}",
                    userEmail, provider, result.getFetchedRemoteCount(), result.getSyncedCalendarCount());

        } catch (SyncException e) {
            syncStatusPersistenceService.markFailed(sync.getId(), e.getMessage());
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            throw e;
        } catch (Exception e) {
            syncStatusPersistenceService.markFailed(sync.getId(), e.getMessage());
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            throw new SyncException("SYNC_FAILED", "Sync failed: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * Incremental sync for a single calendar (triggered from a webhook callback).
     * Status is not updated here — this is a lightweight targeted operation.
     */
    @Transactional
    public void triggerIncrementalSync(CustomerUserSyncEntity sync, CUSyncCalendarEntity calendar) {
        String vendorCode = sync.getSyncVendor() != null
                ? sync.getSyncVendor().getVendorCode() : null;
        if (vendorCode == null) {
            log.warn("No vendor on sync entity — cannot determine provider for incremental sync");
            return;
        }
        ProviderType provider;
        try {
            provider = ProviderType.valueOf(vendorCode);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown vendor code '{}' for incremental sync", vendorCode);
            return;
        }
        ICalendarProvider calendarProvider = calendarProviders.get(provider);
        if (calendarProvider == null) {
            log.warn("No calendar provider found for {}", provider);
            return;
        }
        log.info("Incremental sync triggered for calendar {} via webhook", calendar.getCalendarReference());
        SyncResult result = new SyncResult();
        syncCalendarEvents(sync, calendar, calendarProvider, result);
        log.info("Incremental sync complete: fetched={}", result.getFetchedRemoteCount());
    }

    // ── Calendar list sync ────────────────────────────────────────────────────

    private void fetchAndSaveCalendars(CustomerUserSyncEntity sync, ICalendarProvider provider) {
        try {
            List<ICalendarProvider.CalendarInfo> remoteCalendars = provider.fetchCalendars(sync);
            for (ICalendarProvider.CalendarInfo info : remoteCalendars) {
                providerCalendarService.createOrUpdateCalendar(
                        sync, info.id(), info.name(), info.color(),
                        info.timeZone(), info.isPrimary(), info.isWritable());
            }
            log.info("Calendar list sync: {} calendars for {}", remoteCalendars.size(), sync.getSyncEmail());
        } catch (Exception e) {
            log.warn("Failed to fetch calendar list for {}: {}", sync.getSyncEmail(), e.getMessage());
        }
    }

    // ── Per-calendar event sync ───────────────────────────────────────────────

    private void syncCalendarEvents(CustomerUserSyncEntity sync, CUSyncCalendarEntity calendar,
                                    ICalendarProvider provider, SyncResult result) {
        log.debug("Syncing events for calendar {}", calendar.getCalendarReference());

        ICalendarProvider.SyncFetchResult fetchResult = null;
        try {
            fetchResult = provider.fetchEventsWithToken(sync, calendar.getCalendarReference(), null);
        } catch (CalendarException e) {
            log.debug("Token-based sync not supported for {}: {}", calendar.getCalendarReference(), e.getMessage());
        }

        if (fetchResult == null) {
            OffsetDateTime since = calendar.getLastEventSyncDate() != null
                    ? calendar.getLastEventSyncDate()
                    : OffsetDateTime.now().minusDays(90);
            List<EventDto> events = provider.fetchEvents(sync, calendar.getCalendarReference(), since);
            for (EventDto e : events) mergeEvent(calendar.getId(), e, result);
        } else {
            for (EventDto e : fetchResult.events()) mergeEvent(calendar.getId(), e, result);
        }

        providerCalendarService.updateSyncCursor(calendar);
        result.setSyncedCalendarCount(result.getSyncedCalendarCount() + 1);
    }

    // ── Event upsert (delegates to REQUIRES_NEW service) ─────────────────────

    private void mergeEvent(Long calendarId, EventDto dto, SyncResult result) {
        try {
            boolean merged = eventMergePersistenceService.mergeEvent(calendarId, dto);
            result.setFetchedRemoteCount(result.getFetchedRemoteCount() + 1);
            //log.debug("{} event {}", merged ? "Saved" : "Cancelled/Skipped", dto.getExternalId());
        } catch (Exception e) {
            log.warn("Failed to merge event {} for calendar {}: {}",
                    dto.getExternalId(), calendarId, e.getMessage(), e);
            result.getFailedRemoteEventIds().add(dto.getExternalId());
        }
    }

    // ── SyncResult ────────────────────────────────────────────────────────────

    public static class SyncResult {
        private ProviderType provider;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String status;
        private String errorMessage;
        private long fetchedRemoteCount;
        private int syncedCalendarCount;
        private final List<String> failedRemoteEventIds = new ArrayList<>();
        private final List<String> failedCalendarIds = new ArrayList<>();

        public ProviderType getProvider() { return provider; }
        public void setProvider(ProviderType v) { provider = v; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime v) { startTime = v; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime v) { endTime = v; }
        public String getStatus() { return status; }
        public void setStatus(String v) { status = v; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String v) { errorMessage = v; }
        public long getFetchedRemoteCount() { return fetchedRemoteCount; }
        public void setFetchedRemoteCount(long v) { fetchedRemoteCount = v; }
        public int getSyncedCalendarCount() { return syncedCalendarCount; }
        public void setSyncedCalendarCount(int v) { syncedCalendarCount = v; }
        public List<String> getFailedRemoteEventIds() { return failedRemoteEventIds; }
        public List<String> getFailedCalendarIds() { return failedCalendarIds; }

        @Override
        public String toString() {
            return String.format("SyncResult{provider=%s, fetched=%d, calendars=%d, status=%s}",
                    provider, fetchedRemoteCount, syncedCalendarCount, status);
        }
    }
}
