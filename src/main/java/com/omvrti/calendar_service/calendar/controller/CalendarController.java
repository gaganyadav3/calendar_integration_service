package com.omvrti.calendar_service.calendar.controller;

import com.omvrti.calendar_service.calendar.service.CalendarService;
import com.omvrti.calendar_service.common.dto.CalendarEventDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.CalendarException;
import com.omvrti.calendar_service.persistence.entity.EventEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@Slf4j
public class CalendarController {

    private final CalendarService calendarService;

    private ProviderType parseProviderOrThrow(String provider) {
        try {
            ProviderType providerType = ProviderType.parse(provider);
            log.debug("Resolved provider input '{}' to enum {}", provider, providerType);
            return providerType;
        } catch (IllegalArgumentException e) {
            log.error("Invalid provider input '{}': {}", provider, e.getMessage());
            throw e;
        }
    }

    @GetMapping("/events/fetch")
    public ResponseEntity<?> fetchEvents(@RequestHeader("X-USER-EMAIL") String userEmail, @RequestParam String provider) {
        log.info("Fetching events from {} for user: {}", provider, userEmail);
        ProviderType providerType;
        try {
            providerType = parseProviderOrThrow(provider);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid provider: " + provider);
            return ResponseEntity.badRequest().body(error);
        }
        try {
            List<CalendarEventDto> events = calendarService.fetchEvents(userEmail, providerType);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("provider", provider);
            response.put("count", events.size());
            response.put("events", events);
            return ResponseEntity.ok(response);
        } catch (CalendarException | IllegalStateException e) {
            log.error("Calendar operation failed for provider {} and user {}: {}", providerType, userEmail, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error fetching events", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch events: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/events/save")
    public ResponseEntity<?> saveEvents(@RequestHeader("X-USER-EMAIL") String userEmail, @RequestBody Map<String, Object> request) {
        String provider = (String) request.get("provider");
        List<Map<String, Object>> eventsList = (List<Map<String, Object>>) request.get("events");
        log.info("Saving {} events to {} provider for user: {}", eventsList != null ? eventsList.size() : 0, provider, userEmail);
        ProviderType providerType;
        try {
            providerType = parseProviderOrThrow(provider);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid provider: " + provider);
            return ResponseEntity.badRequest().body(error);
        }
        try {
            List<CalendarEventDto> events = eventsList.stream().map(this::mapToCalendarEventDto).toList();
            calendarService.saveEvents(userEmail, providerType, events);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Events saved successfully");
            response.put("count", events.size());
            return ResponseEntity.ok(response);
        } catch (CalendarException | IllegalStateException e) {
            log.error("Calendar operation failed for provider {} and user {}: {}", providerType, userEmail, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error saving events", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to save events: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/events")
    public ResponseEntity<?> saveEvent(@RequestHeader("X-USER-EMAIL") String userEmail, @RequestParam String provider, @RequestBody CalendarEventDto event) {
        log.info("Saving event to {} provider for user: {}", provider, userEmail);
        ProviderType providerType;
        try {
            providerType = parseProviderOrThrow(provider);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid provider: " + provider);
            return ResponseEntity.badRequest().body(error);
        }
        try {
            String eventId = calendarService.saveEvent(userEmail, providerType, event);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("eventId", eventId);
            response.put("message", "Event saved successfully");
            return ResponseEntity.ok(response);
        } catch (CalendarException | IllegalStateException e) {
            log.error("Calendar operation failed for provider {} and user {}: {}", providerType, userEmail, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error saving event", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to save event: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PutMapping("/events/{eventId}")
    public ResponseEntity<?> updateEvent(@RequestHeader("X-USER-EMAIL") String userEmail, @PathVariable String eventId, @RequestParam String provider, @RequestBody CalendarEventDto event) {
        log.info("Updating event {} in {} provider for user: {}", eventId, provider, userEmail);
        ProviderType providerType;
        try {
            providerType = parseProviderOrThrow(provider);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid provider: " + provider);
            return ResponseEntity.badRequest().body(error);
        }
        try {
            calendarService.updateEvent(userEmail, providerType, eventId, event);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Event updated successfully");
            return ResponseEntity.ok(response);
        } catch (CalendarException | IllegalStateException e) {
            log.error("Calendar operation failed for provider {} and user {}: {}", providerType, userEmail, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error updating event", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to update event: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<?> deleteEvent(@RequestHeader("X-USER-EMAIL") String userEmail, @PathVariable String eventId, @RequestParam String provider) {
        log.info("Deleting event {} from {} provider for user: {}", eventId, provider, userEmail);
        ProviderType providerType;
        try {
            providerType = parseProviderOrThrow(provider);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid provider: " + provider);
            return ResponseEntity.badRequest().body(error);
        }
        try {
            calendarService.deleteEvent(userEmail, providerType, eventId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Event deleted successfully");
            return ResponseEntity.ok(response);
        } catch (CalendarException | IllegalStateException e) {
            log.error("Calendar operation failed for provider {} and user {}: {}", providerType, userEmail, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error deleting event", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to delete event: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/events")
    public ResponseEntity<?> getUserEvents(@RequestHeader("X-USER-EMAIL") String userEmail) {
        log.info("Fetching all events from database for user: {}", userEmail);
        try {
            List<EventEntity> events = calendarService.getUserEvents(userEmail);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", events.size());
            response.put("events", events);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching user events", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch events: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/events/by-provider")
    public ResponseEntity<?> getUserEventsByProvider(@RequestHeader("X-USER-EMAIL") String userEmail, @RequestParam String provider) {
        log.info("Fetching events from {} for user: {} from database", provider, userEmail);
        ProviderType providerType;
        try {
            providerType = parseProviderOrThrow(provider);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid provider: " + provider);
            return ResponseEntity.badRequest().body(error);
        }
        try {
            List<EventEntity> events = calendarService.getUserEventsByProvider(userEmail, providerType);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("provider", provider);
            response.put("count", events.size());
            response.put("events", events);
            return ResponseEntity.ok(response);
        } catch (CalendarException | IllegalStateException e) {
            log.error("Calendar operation failed for provider {} and user {}: {}", providerType, userEmail, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error fetching provider events", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch events: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    private CalendarEventDto mapToCalendarEventDto(Map<String, Object> map) {
        return CalendarEventDto.builder()
                .id((String) map.get("id"))
                .summary((String) map.get("summary"))
                .description((String) map.get("description"))
                .location((String) map.get("location"))
                .organizer((String) map.get("organizer"))
                .status((String) map.get("status"))
                .allDay((Boolean) map.getOrDefault("allDay", false))
                .build();
    }
}
