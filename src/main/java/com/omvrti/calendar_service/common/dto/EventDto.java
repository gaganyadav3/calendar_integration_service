package com.omvrti.calendar_service.common.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.util.FlexibleLocalDateDeserializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventDto {
    
    private Long id;
    private String internalId;
    private String externalId;
    
    private String title;
    private String description;
    private String location;
    private String organizer;
    
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    
    private String timeZoneId;
    private boolean allDay;
    
    private String status;
    private boolean isCancelled;
    
    private ProviderType provider;
    private EventSource source;
    
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime externalUpdatedAt;
    
    private String syncStatus;
    private Long version;
    
    private List<AttendeeDto> attendees;

    public boolean isUpcoming() {
        return endTime.isAfter(OffsetDateTime.now());
    }
    
    public boolean isBooking() {
        return !isCancelled && isUpcoming();
    }
}
