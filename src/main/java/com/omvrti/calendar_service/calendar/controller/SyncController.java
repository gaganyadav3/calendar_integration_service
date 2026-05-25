package com.omvrti.calendar_service.calendar.controller;

import com.omvrti.calendar_service.calendar.service.VendorListService;
import com.omvrti.calendar_service.calendar.sync.ScheduledSyncService;
import com.omvrti.calendar_service.common.dto.VendorConnectionStatusResponse;
import com.omvrti.calendar_service.common.dto.VendorDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SyncController {

    private final ScheduledSyncService scheduledSyncService;
    private final VendorListService vendorListService;

    // ── Force sync ────────────────────────────────────────────────────────────

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

    // ── GET /api/sync/vendors ─────────────────────────────────────────────────

    /**
     * Returns all active sync vendors from SYNC_VENDOR + SYNC_VENDOR_LANG.
     * If X-USER-EMAIL header is provided, each vendor is enriched with the
     * user's connection status (connected, connectedEmail).
     *
     * Query params:
     *   languageId (optional, default=1) — selects the language row from SYNC_VENDOR_LANG
     */
    @GetMapping("/api/sync/vendors")
    public ResponseEntity<?> getVendors(
            @RequestHeader(value = "X-USER-EMAIL", required = false) String userEmail,
            @RequestParam(value = "languageId", required = false, defaultValue = "1") Integer languageId) {
        try {
            List<VendorDto> vendors = vendorListService.getVendors(userEmail, languageId);
            return ResponseEntity.ok(vendors);
        } catch (Exception e) {
            log.error("Get vendors failed for user={}", userEmail, e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    // ── GET /api/sync/vendors/status ──────────────────────────────────────────

    /**
     * Returns detailed connection status for every active vendor for the given user.
     * Includes: syncStatus, connectedEmail, lastSyncDate, calendarCount, webhookActive.
     *
     * Header: X-USER-EMAIL (required)
     */
    @GetMapping("/api/sync/vendors/status")
    public ResponseEntity<?> getVendorConnectionStatus(
            @RequestHeader("X-USER-EMAIL") String userEmail) {
        try {
            VendorConnectionStatusResponse response = vendorListService.getConnectionStatus(userEmail);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Vendor status request for unknown user={}: {}", userEmail, e.getMessage());
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            log.error("Get vendor status failed for user={}", userEmail, e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }
}
