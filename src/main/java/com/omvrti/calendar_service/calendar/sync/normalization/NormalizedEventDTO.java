package com.omvrti.calendar_service.calendar.sync.normalization;

import com.omvrti.calendar_service.common.enums.ProviderType;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class NormalizedEventDTO {
    String externalId;
    String iCalUID;
    String recurringEventId;
    String title;
    String description;
    String location;
    String organizerEmail;
    String meetingUrl;
    OffsetDateTime startTime;
    OffsetDateTime endTime;
    String timeZoneId;
    boolean allDay;
    String status;
    boolean cancelled;
    ProviderType provider;
    OffsetDateTime externalUpdatedAt;
    OffsetDateTime createdAt;
    List<NormalizedGuestDTO> attendees;
    List<String> recurrenceRules;
    String conferenceData;
    String htmlLink;
    Integer sequence;
    String etag;
    String transparency;
    String visibility;
    OffsetDateTime originalStartDate;
    String originalStartTimezone;
}
