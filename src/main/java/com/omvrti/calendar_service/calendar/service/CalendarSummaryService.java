package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.common.dto.CalendarSummaryDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.repository.CustomerUserRepository;
import com.omvrti.calendar_service.persistence.repository.CustomerUserSyncRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarSummaryService {

    private final CustomerUserRepository customerUserRepository;
    private final CustomerUserSyncRepository customerUserSyncRepository;
    private final CustomerUserSyncService customerUserSyncService;
    private final EventManagementService eventManagementService;
    private final TokenRefreshService tokenRefreshService;
    private final Map<ProviderType, ICalendarProvider> calendarProviders;

    public CalendarSummaryDto.SummaryResponse getCalendarSummary(String userEmail) {
        log.debug("Generating calendar summary for: {}", userEmail);

        var customerUser = customerUserRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        List<CustomerUserSyncEntity> activeSyncs = customerUserSyncService.getActiveSyncs(customerUser);

        CalendarSummaryDto.SummaryResponse response = new CalendarSummaryDto.SummaryResponse();
        Map<String, CalendarSummaryDto> providerSummaries = new HashMap<>();
        long totalEvents = 0;
        long totalBookings = 0;

        for (CustomerUserSyncEntity sync : activeSyncs) {
            try {
                ProviderType provider = ProviderType.valueOf(sync.getSyncVendor().getVendorCode());
                long events = eventManagementService.countEvents(userEmail, provider);
                long bookings = eventManagementService.getUpcomingEvents(userEmail).stream()
                        .filter(e -> !Boolean.TRUE.equals(e.getIsCancelled())).count();

                CalendarSummaryDto summary = CalendarSummaryDto.builder()
                        .provider(provider)
                        .totalEvents(events)
                        .totalBookings(bookings)
                        .isConnected(Integer.valueOf(1).equals(sync.getIsActive()))
                        .lastSyncStatus(sync.getSyncStatus() != null ? sync.getSyncStatus().getName() : "UNKNOWN")
                        .build();
                providerSummaries.put(provider.name(), summary);
                totalEvents += events;
                totalBookings += bookings;
            } catch (Exception e) {
                log.warn("Failed to get summary for sync {}: {}", sync.getId(), e.getMessage());
            }
        }

        response.setTotalProvidersConnected(activeSyncs.size());
        response.setTotalUnifiedEvents(totalEvents);
        response.setTotalUnifiedBookings(totalBookings);
        response.setProviderSummaries(providerSummaries);
        return response;
    }

    public ICalendarProvider.CalendarStateSummary getProviderStateSummary(String userEmail, ProviderType provider) {
        CustomerUserSyncEntity sync = customerUserSyncService.getSyncByEmail(userEmail, provider)
                .filter(s -> Integer.valueOf(1).equals(s.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Provider not connected: " + provider));

        ICalendarProvider calendarProvider = calendarProviders.get(provider);
        if (calendarProvider == null) throw new IllegalArgumentException("Provider not implemented: " + provider);

        try {
            tokenRefreshService.getValidAccessToken(sync, provider);
            return calendarProvider.getCurrentStateSummary(sync, "primary");
        } catch (Exception e) {
            log.error("Error getting provider state summary", e);
            throw new RuntimeException("Failed to get state summary: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getEventStatistics(String userEmail) {
        log.debug("Calculating event statistics for: {}", userEmail);
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEvents", eventManagementService.getUserEvents(userEmail).size());
        stats.put("upcomingBookings", eventManagementService.getUpcomingEvents(userEmail).size());
        return stats;
    }
}
