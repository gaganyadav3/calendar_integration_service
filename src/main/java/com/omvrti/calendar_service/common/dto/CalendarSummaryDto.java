package com.omvrti.calendar_service.common.dto;

import com.omvrti.calendar_service.common.enums.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarSummaryDto {

    private ProviderType provider;
    private long totalEvents;
    private long totalBookings;
    private boolean isConnected;
    private String lastSyncStatus;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SummaryResponse {
        private long totalProvidersConnected;
        private long totalUnifiedEvents;
        private long totalUnifiedBookings;
        private java.util.Map<String, CalendarSummaryDto> providerSummaries;
    }
}

