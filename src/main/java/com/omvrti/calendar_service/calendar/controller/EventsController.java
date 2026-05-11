package com.omvrti.calendar_service.calendar.controller;

import com.omvrti.calendar_service.calendar.service.UnifiedEventService;
import com.omvrti.calendar_service.common.dto.EventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class EventsController {

    private final UnifiedEventService unifiedEventService;

    @PostMapping("/events")
    public ResponseEntity<?> create(
            @RequestHeader("X-USER-EMAIL") String userEmail,
            @RequestBody EventDto body) {
        try {
            EventDto created = unifiedEventService.create(userEmail, body);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            log.error("Create event failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @GetMapping("/events")
    public ResponseEntity<?> list(@RequestHeader("X-USER-EMAIL") String userEmail) {
        return ResponseEntity.ok(unifiedEventService.list(userEmail));
    }

    @PutMapping("/events/{id}")
    public ResponseEntity<?> update(
            @RequestHeader("X-USER-EMAIL") String userEmail,
            @PathVariable("id") String internalId,
            @RequestBody EventDto body) {
        try {
            return ResponseEntity.ok(unifiedEventService.update(userEmail, internalId, body));
        } catch (Exception e) {
            log.error("Update event failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-USER-EMAIL") String userEmail,
            @PathVariable("id") String internalId) {
        try {
            unifiedEventService.delete(userEmail, internalId);
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            return ResponseEntity.ok(ok);
        } catch (Exception e) {
            log.error("Delete event failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }
}

