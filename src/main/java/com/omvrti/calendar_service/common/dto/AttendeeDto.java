package com.omvrti.calendar_service.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendeeDto {
    private String email;
    private String name;
    private String status;

    /** True if this attendee marked optional by the organizer. */
    private boolean optional;

    /** True if this attendee is the event organizer (Google attendees[].organizer). */
    private boolean organizer;

    /** True if this attendee is a room/resource, not a person. Maps to IS_HUMAN=0. */
    private boolean resource;

    /** Free-text attendee comment (Google attendees[].comment; Outlook has no equivalent). */
    private String comment;
}