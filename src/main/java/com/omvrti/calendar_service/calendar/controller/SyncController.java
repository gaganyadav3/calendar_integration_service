package com.omvrti.calendar_service.calendar.controller;

import com.omvrti.calendar_service.calendar.sync.ScheduledSyncService;
import com.omvrti.calendar_service.common.enums.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SyncController {

    private final ScheduledSyncService scheduledSyncService;

    @PostMapping({"/sync/{provider}", "/api/sync/{provider}"})
    public ResponseEntity<?> forceSync(
            @RequestHeader("X-USER-EMAIL") String userEmail,
            @PathVariable String provider) {
        try {
            ProviderType providerType = ProviderType.parse(provider);
            return ResponseEntity.ok(scheduledSyncService.forceSyncAccount(userEmail, providerType));
        } catch (Exception e) {
            log.error("Force sync failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }
}
