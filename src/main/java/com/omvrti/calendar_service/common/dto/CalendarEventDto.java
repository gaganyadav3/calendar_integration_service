package com.omvrti.calendar_service.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.omvrti.calendar_service.common.util.FlexibleLocalDateDeserializer;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEventDto {
    private String id;
    private String summary;
    private String description;

    private OffsetDateTime startDateTime;
    private OffsetDateTime endDateTime;

    @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
    private LocalDate startDate;

    @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
    private LocalDate endDate;

    private String status;
    private String location;
    private String organizer;
    private boolean allDay;
}


