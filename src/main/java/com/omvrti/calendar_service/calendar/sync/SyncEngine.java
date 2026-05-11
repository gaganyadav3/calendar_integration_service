package com.omvrti.calendar_service.calendar.sync;

import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.calendar.service.EventManagementService;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.CalendarException;
import com.omvrti.calendar_service.common.exception.SyncException;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import com.omvrti.calendar_service.persistence.entity.EventEntity;
import com.omvrti.calendar_service.persistence.entity.SyncMetadataEntity;
import com.omvrti.calendar_service.persistence.entity.UserEntity;
import com.omvrti.calendar_service.persistence.repository.ConnectedAccountRepository;
import com.omvrti.calendar_service.persistence.repository.EventRepository;
import com.omvrti.calendar_service.persistence.repository.SyncMetadataRepository;
import com.omvrti.calendar_service.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core sync engine for two-way calendar synchronization
 * Handles pulling events from providers and pushing local changes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncEngine {

    private final ConnectedAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SyncMetadataRepository syncMetadataRepository;
    private final EventManagementService eventManagementService;
    private final TokenRefreshService tokenRefreshService;
    private final Map<ProviderType, ICalendarProvider> calendarProviders;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SYNC_INTERVAL_MINUTES = 5;

    /**
     * Perform full two-way sync for a user and provider
     */
    @Transactional
    public SyncResult sync(String userEmail, ProviderType provider) {
        log.info("Starting sync for {} - {}", userEmail, provider);

        try {
            UserEntity user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new SyncException("USER_NOT_FOUND", "User not found: " + userEmail));

            ConnectedAccountEntity account = accountRepository.findByUserAndProvider(user, provider)
                    .orElseThrow(() -> new SyncException("ACCOUNT_NOT_CONNECTED", "Account not connected: " + provider));

            if (!account.isActive()) {
                throw new SyncException("ACCOUNT_INACTIVE", "Account is not active: " + provider);
            }

            SyncResult result = new SyncResult();
            result.setProvider(provider);
            result.setStartTime(LocalDateTime.now());

            try {
                // Refresh token if needed
                tokenRefreshService.getValidAccessToken(userEmail, provider);

                // 1. Pull changes from provider
                pullRemoteChanges(user, account, result);

                // 2. Push local changes to provider
                pushLocalChanges(user, account, result);

                // 3. Update sync metadata
                updateSyncMetadata(user, provider, result, true);

                result.setStatus("SUCCESS");
                result.setEndTime(LocalDateTime.now());
                log.info("Sync completed successfully for {} - {}", userEmail, provider);

            } catch (Exception e) {
                log.error("Sync failed for {} - {}", userEmail, provider, e);
                result.setStatus("FAILED");
                result.setErrorMessage(e.getMessage());
                result.setEndTime(LocalDateTime.now());

                updateSyncMetadata(user, provider, result, false);
                throw new SyncException("SYNC_FAILED", "Sync failed: " + e.getMessage(), e);
            }

            return result;
        } catch (Exception e) {
            log.error("Sync error", e);
            throw e instanceof SyncException ? (SyncException) e :
                  new SyncException("SYNC_ERROR", "Sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Pull remote changes from provider and merge locally.
     * Uses sync-token-based incremental sync when the provider supports it (Google).
     * Falls back to time-based fetch for providers that do not support sync tokens.
     */
    private void pullRemoteChanges(UserEntity user, ConnectedAccountEntity account, SyncResult result) {
        log.debug("Pulling remote changes from {}", account.getProvider());

        try {
            ICalendarProvider provider = calendarProviders.get(account.getProvider());
            if (provider == null) {
                throw new SyncException("PROVIDER_NOT_FOUND", "Provider not found: " + account.getProvider());
            }

            SyncMetadataEntity metadata = syncMetadataRepository
                    .findByUserAndProvider(user, account.getProvider())
                    .orElse(null);

            List<EventDto> remoteEvents;
            ICalendarProvider.SyncFetchResult syncFetchResult = null;

            // Try sync-token path first (Google supports this; others return null)
            String existingSyncToken = metadata != null ? metadata.getLastSyncToken() : null;

            if (existingSyncToken != null) {
                log.info("Incremental sync started for {} - {} using sync token", user.getEmail(), account.getProvider());
            } else {
                log.info("Full sync started for {} - {} (no sync token)", user.getEmail(), account.getProvider());
            }

            try {
                syncFetchResult = provider.fetchEventsWithToken(account, existingSyncToken);
            } catch (CalendarException e) {
                if ("SYNC_TOKEN_EXPIRED".equals(e.getErrorCode())) {
                    log.warn("Sync token expired for {} - {}. Clearing token and performing full resync.",
                            user.getEmail(), account.getProvider());
                    if (metadata != null) {
                        metadata.setLastSyncToken(null);
                        syncMetadataRepository.save(metadata);
                    }
                    log.info("Full resync started for {} - {}", user.getEmail(), account.getProvider());
                    syncFetchResult = provider.fetchEventsWithToken(account, null);
                } else {
                    throw e;
                }
            }

            if (syncFetchResult != null) {
                remoteEvents = syncFetchResult.events();
                String nextSyncToken = syncFetchResult.nextSyncToken();
                if (nextSyncToken != null) {
                    SyncMetadataEntity meta = metadata != null ? metadata
                            : SyncMetadataEntity.builder().user(user).provider(account.getProvider()).build();
                    meta.setLastSyncToken(nextSyncToken);
                    syncMetadataRepository.save(meta);
                }
                log.info("Sync fetch complete for {} - {}: {} events", user.getEmail(), account.getProvider(), remoteEvents.size());
            } else {
                // Provider does not support sync tokens — fall back to time-based fetch
                OffsetDateTime syncSince = metadata != null && metadata.getLastSyncTime() != null
                        ? metadata.getLastSyncTime().atZone(ZoneOffset.UTC).toOffsetDateTime()
                        : OffsetDateTime.now().minusDays(30);
                remoteEvents = provider.fetchEvents(account, syncSince);
                log.info("Time-based sync fetched {} events from {}", remoteEvents.size(), account.getProvider());
            }

            for (EventDto remoteEvent : remoteEvents) {
                try {
                    mergeRemoteEvent(user, account, remoteEvent);
                } catch (Exception e) {
                    log.warn("Failed to merge remote event {}", remoteEvent.getExternalId(), e);
                    result.getFailedRemoteEventIds().add(remoteEvent.getExternalId());
                }
            }

            result.setFetchedRemoteCount(remoteEvents.size());

        } catch (Exception e) {
            log.error("Error pulling remote changes for {} - {}", user.getEmail(), account.getProvider(), e);
            throw new SyncException("PULL_FAILED", "Failed to pull remote changes: " + e.getMessage(), e);
        }
    }

    /**
     * Merge a remote event into local database.
     * Cancelled events mark the local record deleted; all others are upserted.
     */
    private void mergeRemoteEvent(UserEntity user, ConnectedAccountEntity account, EventDto remoteEvent) {
        log.debug("Merging remote event: {}", remoteEvent.getExternalId());

        List<EventEntity> existingList = eventRepository.findByExternalIdAndProvider(
                remoteEvent.getExternalId(), account.getProvider());
        Optional<EventEntity> existing = existingList.isEmpty()
                ? Optional.empty() : Optional.of(existingList.get(0));

        if (remoteEvent.isCancelled()) {
            // Mark cancelled/deleted events locally without creating a new record
            existing.ifPresent(local -> {
                if (!local.isDeleted() || !local.isCancelled()) {
                    log.debug("Marking event as cancelled/deleted: {}", remoteEvent.getExternalId());
                    local.setDeleted(true);
                    local.setCancelled(true);
                    eventRepository.save(local);
                }
            });
            return;
        }

        if (existing.isPresent()) {
            EventEntity local = existing.get();
            boolean remoteIsNewer = remoteEvent.getExternalUpdatedAt() == null
                    || local.getExternalUpdatedAt() == null
                    || remoteEvent.getExternalUpdatedAt().isAfter(local.getExternalUpdatedAt());

            if (remoteIsNewer) {
                log.debug("Updating local event with remote changes: {}", remoteEvent.getExternalId());
                remoteEvent.setInternalId(local.getInternalId());
                remoteEvent.setSource(EventSource.valueOf(account.getProvider().name()));
                remoteEvent.setProvider(account.getProvider());
                eventManagementService.saveEvent(user.getEmail(), remoteEvent, account);
            }
        } else {
            log.debug("Adding new remote event: {}", remoteEvent.getExternalId());
            remoteEvent.setSource(EventSource.valueOf(account.getProvider().name()));
            remoteEvent.setProvider(account.getProvider());
            eventManagementService.saveEvent(user.getEmail(), remoteEvent, account);
        }
    }

    /**
     * Push local changes to provider
     */
    private void pushLocalChanges(UserEntity user, ConnectedAccountEntity account, SyncResult result) {
        log.debug("Pushing local changes to {}", account.getProvider());

        try {
            ICalendarProvider provider = calendarProviders.get(account.getProvider());
            if (provider == null) {
                throw new SyncException("PROVIDER_NOT_FOUND", "Provider not found: " + account.getProvider());
            }

            // Get events that were created/updated locally (by INTERNAL source) after last sync
            Optional<SyncMetadataEntity> metadata = syncMetadataRepository.findByUserAndProvider(user, account.getProvider());
            OffsetDateTime pushSince = metadata
                    .map(m -> m.getLastSyncTime() != null ?
                        m.getLastSyncTime().atZone(ZoneOffset.UTC).toOffsetDateTime() : null)
                    .orElse(OffsetDateTime.now().minusDays(30));

            List<EventEntity> localChanges = eventRepository.findByUserAndProviderAndUpdatedAtAfter(
                user, account.getProvider(), pushSince
            ).stream()
                    .filter(e -> e.getSource() == EventSource.INTERNAL)
                    .collect(Collectors.toList());

            log.info("Pushing {} local changes", localChanges.size());

            for (EventEntity localEvent : localChanges) {
                try {
                    pushLocalEvent(provider, account, localEvent);
                    result.setPushedLocalCount(result.getPushedLocalCount() + 1);
                } catch (Exception e) {
                    log.warn("Failed to push local event", e);
                    result.getFailedLocalEventIds().add(localEvent.getInternalId());
                }
            }

            log.debug("Pushed {} local changes", result.getPushedLocalCount());

        } catch (Exception e) {
            log.error("Error pushing local changes", e);
            throw new SyncException("PUSH_FAILED", "Failed to push local changes: " + e.getMessage(), e);
        }
    }

    /**
     * Push a single local event to provider
     */
    private void pushLocalEvent(ICalendarProvider provider, ConnectedAccountEntity account, EventEntity localEvent) {
        log.debug("Pushing local event: {}", localEvent.getInternalId());

        if (localEvent.isDeleted()) {
            if (localEvent.getExternalId() != null) {
                provider.deleteEvent(account, localEvent.getExternalId());
                log.debug("Deleted event in provider: {}", localEvent.getExternalId());
            }
            return;
        }

        EventDto dto = convertEntityToDto(localEvent);

        if (localEvent.getExternalId() == null) {
            // Create new event in provider
            String externalId = provider.createEvent(account, dto);
            localEvent.setExternalId(externalId);
            eventRepository.save(localEvent);
            log.debug("Created new event in provider: {}", externalId);
        } else {
            // Update existing event in provider
            provider.updateEvent(account, localEvent.getExternalId(), dto);
            log.debug("Updated event in provider: {}", localEvent.getExternalId());
        }
    }

    /**
     * Update sync metadata after sync completes
     */
    @Transactional
    protected void updateSyncMetadata(UserEntity user, ProviderType provider, SyncResult result, boolean success) {
        log.debug("Updating sync metadata for {} - {}", user.getEmail(), provider);

        SyncMetadataEntity metadata = syncMetadataRepository.findByUserAndProvider(user, provider)
                .orElse(SyncMetadataEntity.builder()
                    .user(user)
                    .provider(provider)
                    .build());

        if (success) {
            metadata.setLastSyncTime(LocalDateTime.now());
            metadata.setLastSyncCount((int) (result.getFetchedRemoteCount() + result.getPushedLocalCount()));
            metadata.setLastSyncStatus("SUCCESS");
            metadata.setConsecutiveFailures(0);
            metadata.setLastErrorMessage(null);
        } else {
            metadata.setLastSyncStatus("FAILED");
            metadata.setLastErrorMessage(result.getErrorMessage());
            metadata.setConsecutiveFailures((metadata.getConsecutiveFailures() != null ?
                metadata.getConsecutiveFailures() : 0) + 1);
        }

        syncMetadataRepository.save(metadata);
    }

    private EventDto convertEntityToDto(EventEntity entity) {
        return EventDto.builder()
                .internalId(entity.getInternalId())
                .externalId(entity.getExternalId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .location(entity.getLocation())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .timeZoneId(entity.getTimeZoneId())
                .allDay(entity.isAllDay())
                .status(entity.getStatus())
                .isCancelled(entity.isCancelled())
                .build();
    }

    /**
     * Result of a sync operation
     */
    public static class SyncResult {
        private ProviderType provider;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String status;
        private String errorMessage;
        private long fetchedRemoteCount;
        private long pushedLocalCount;
        private List<String> failedRemoteEventIds = new ArrayList<>();
        private List<String> failedLocalEventIds = new ArrayList<>();

        // Getters and setters
        public ProviderType getProvider() { return provider; }
        public void setProvider(ProviderType provider) { this.provider = provider; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getFetchedRemoteCount() { return fetchedRemoteCount; }
        public void setFetchedRemoteCount(long count) { this.fetchedRemoteCount = count; }
        public long getPushedLocalCount() { return pushedLocalCount; }
        public void setPushedLocalCount(long count) { this.pushedLocalCount = count; }
        public List<String> getFailedRemoteEventIds() { return failedRemoteEventIds; }
        public List<String> getFailedLocalEventIds() { return failedLocalEventIds; }

        @Override
        public String toString() {
            return String.format("SyncResult{provider=%s, fetched=%d, pushed=%d, status=%s}",
                provider, fetchedRemoteCount, pushedLocalCount, status);
        }
    }
}

