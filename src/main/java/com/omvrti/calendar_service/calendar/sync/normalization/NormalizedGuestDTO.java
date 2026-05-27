package com.omvrti.calendar_service.calendar.sync.normalization;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NormalizedGuestDTO {
    String email;
    String name;
    String status;
    boolean optional;
    boolean organizer;
    boolean resource;
    String comment;
}
