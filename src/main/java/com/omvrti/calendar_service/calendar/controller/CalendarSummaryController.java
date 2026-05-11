package com.omvrti.calendar_service.calendar.controller;

import com.omvrti.calendar_service.calendar.service.CalendarSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class CalendarSummaryController {

    private final CalendarSummaryService calendarSummaryService;

    @GetMapping({"/calendar/summary", "/api/calendar/summary"})
    public ResponseEntity<?> summary(@RequestHeader("X-USER-EMAIL") String userEmail) {
        return ResponseEntity.ok(calendarSummaryService.getCalendarSummary(userEmail));
    }
}

