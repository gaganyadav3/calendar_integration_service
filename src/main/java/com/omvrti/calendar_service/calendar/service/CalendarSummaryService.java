package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.common.dto.CalendarSummaryDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import com.omvrti.calendar_service.persistence.entity.UserEntity;
import com.omvrti.calendar_service.persistence.repository.ConnectedAccountRepository;
import com.omvrti.calendar_service.persistence.repository.EventRepository;
import com.omvrti.calendar_service.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating calendar summaries and statistics
 * Calculates total events, bookings, and provider status
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarSummaryService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ConnectedAccountRepository accountRepository;
    private final TokenRefreshService tokenRefreshService;
    private final Map<ProviderType, ICalendarProvider> calendarProviders;

    /**
     * Get comprehensive calendar summary for a user
     * Returns total events, bookings, and per-provider status
     */
    public CalendarSummaryDto.SummaryResponse getCalendarSummary(String userEmail) {
        log.debug("Generating calendar summary for: {}", userEmail);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        // Get all connected provider accounts
        List<ConnectedAccountEntity> accounts = accountRepository.findByUserAndIsActiveTrue(user);

        CalendarSummaryDto.SummaryResponse response = new CalendarSummaryDto.SummaryResponse();
        Map<String, CalendarSummaryDto> providerSummaries = new HashMap<>();

        long totalUnifiedEvents = 0;
        long totalUnifiedBookings = 0;

        for (ConnectedAccountEntity account : accounts) {
            CalendarSummaryDto summary = getProviderSummary(user, account);
            providerSummaries.put(account.getProvider().name(), summary);

            totalUnifiedEvents += summary.getTotalEvents();
            totalUnifiedBookings += summary.getTotalBookings();
        }

        response.setTotalProvidersConnected(accounts.size());
        response.setTotalUnifiedEvents(totalUnifiedEvents);
        response.setTotalUnifiedBookings(totalUnifiedBookings);
        response.setProviderSummaries(providerSummaries);

        log.debug("Calendar summary generated: {} providers, {} events, {} bookings",
            accounts.size(), totalUnifiedEvents, totalUnifiedBookings);

        return response;
    }

    /**
     * Get summary for a specific provider
     */
    public CalendarSummaryDto getProviderSummary(UserEntity user, ConnectedAccountEntity account) {
        log.debug("Getting summary for {} - {}", user.getEmail(), account.getProvider());

        long totalEvents = eventRepository.countEventsByUserAndProvider(user, account.getProvider());
        long totalBookings = eventRepository.countUpcomingBookingsByUserAndProvider(user, account.getProvider());

        CalendarSummaryDto summary = CalendarSummaryDto.builder()
                .provider(account.getProvider())
                .totalEvents(totalEvents)
                .totalBookings(totalBookings)
                .isConnected(account.isActive())
                .lastSyncStatus(getLastSyncStatus(account))
                .build();

        return summary;
    }

    /**
     * Get provider-specific summary using provider's API
     * This fetches real-time data from the provider
     */
    public ICalendarProvider.CalendarStateSummary getProviderStateSummary(String userEmail, ProviderType provider) {
        log.debug("Getting provider state summary for {} - {}", userEmail, provider);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        ConnectedAccountEntity account = accountRepository.findByUserAndProvider(user, provider)
                .orElseThrow(() -> new IllegalArgumentException("Provider not connected: " + provider));

        if (!account.isActive()) {
            throw new IllegalArgumentException("Account not active");
        }

        ICalendarProvider calendarProvider = calendarProviders.get(provider);
        if (calendarProvider == null) {
            throw new IllegalArgumentException("Provider not implemented: " + provider);
        }

        try {
            // Ensure token is valid
            tokenRefreshService.getValidAccessToken(userEmail, provider);

            // Get summary from provider
            return calendarProvider.getCurrentStateSummary(account);
        } catch (Exception e) {
            log.error("Error getting provider state summary", e);
            throw new RuntimeException("Failed to get state summary: " + e.getMessage(), e);
        }
    }

    /**
     * Get event statistics for date range
     */
    public Map<String, Object> getEventStatistics(String userEmail) {
        log.debug("Calculating event statistics for: {}", userEmail);

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        Map<String, Object> stats = new HashMap<>();

        // Total events
        long totalEvents = eventRepository.findByUserAndIsDeletedFalse(user).size();
        stats.put("totalEvents", totalEvents);

        // Upcoming bookings
        long upcomingBookings = eventRepository.findUpcomingBookings(user).size();
        stats.put("upcomingBookings", upcomingBookings);

        // Stats by provider
        Map<String, Map<String, Long>> providerStats = new HashMap<>();

        List<ConnectedAccountEntity> accounts = accountRepository.findByUserAndIsActiveTrue(user);
        for (ConnectedAccountEntity account : accounts) {
            Map<String, Long> ps = new HashMap<>();
            ps.put("totalEvents", eventRepository.countEventsByUserAndProvider(user, account.getProvider()));
            ps.put("upcomingBookings", eventRepository.countUpcomingBookingsByUserAndProvider(user, account.getProvider()));
            providerStats.put(account.getProvider().name(), ps);
        }

        stats.put("byProvider", providerStats);

        return stats;
    }

    private String getLastSyncStatus(ConnectedAccountEntity account) {
        // This would typically fetch from sync_metadata table
        // For now returning a placeholder
        return account.getLastTokenRefreshAt() != null ? "OK" : "PENDING";
    }
}

