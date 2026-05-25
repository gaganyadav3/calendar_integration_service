package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.common.dto.VendorConnectionStatusResponse;
import com.omvrti.calendar_service.common.dto.VendorDto;
import com.omvrti.calendar_service.common.dto.VendorStatusDto;
import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEntity;
import com.omvrti.calendar_service.persistence.entity.CustomerUserEntity;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.entity.SyncVendorEntity;
import com.omvrti.calendar_service.persistence.entity.SyncVendorLangEntity;
import com.omvrti.calendar_service.persistence.repository.CUSyncCalendarRepository;
import com.omvrti.calendar_service.persistence.repository.CUSyncCalendarWebhookRepository;
import com.omvrti.calendar_service.persistence.repository.CustomerUserRepository;
import com.omvrti.calendar_service.persistence.repository.CustomerUserSyncRepository;
import com.omvrti.calendar_service.persistence.repository.SyncVendorLangRepository;
import com.omvrti.calendar_service.persistence.repository.SyncVendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorListService {

    private final SyncVendorRepository syncVendorRepository;
    private final SyncVendorLangRepository syncVendorLangRepository;
    private final CustomerUserRepository customerUserRepository;
    private final CustomerUserSyncRepository customerUserSyncRepository;
    private final CUSyncCalendarRepository cuSyncCalendarRepository;
    private final CUSyncCalendarWebhookRepository webhookRepository;

    /**
     * GET /api/sync/vendors
     * Returns all active vendors ordered by DISPLAY_SORT_ORDER.
     * Enriches each with connection status when userEmail is provided.
     */
    @Transactional(readOnly = true)
    public List<VendorDto> getVendors(String userEmail, Integer languageId) {
        int langId = (languageId != null) ? languageId : 1;

        List<SyncVendorEntity> vendors = syncVendorRepository
                .findByIsValidOrderByDisplaySortOrderAsc(1);

        CustomerUserEntity user = null;
        if (userEmail != null && !userEmail.isBlank()) {
            user = customerUserRepository.findByEmail(userEmail).orElse(null);
            if (user == null) {
                log.debug("No customer user found for email={}, returning vendors without connection status", userEmail);
            }
        }

        List<VendorDto> result = new ArrayList<>();
        for (SyncVendorEntity vendor : vendors) {
            SyncVendorLangEntity lang = resolveLang(vendor, langId);
            CustomerUserSyncEntity sync = findSync(user, vendor);
            result.add(buildVendorDto(vendor, lang, sync));
        }
        log.debug("Returning {} vendors for user={}", result.size(), userEmail);
        return result;
    }

    /**
     * GET /api/sync/vendors/status
     * Returns detailed per-vendor connection status for a given user.
     */
    @Transactional(readOnly = true)
    public VendorConnectionStatusResponse getConnectionStatus(String userEmail) {
        CustomerUserEntity user = customerUserRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Customer user not found: " + userEmail));

        List<SyncVendorEntity> vendors = syncVendorRepository
                .findByIsValidOrderByDisplaySortOrderAsc(1);

        List<VendorStatusDto> statusList = new ArrayList<>();
        for (SyncVendorEntity vendor : vendors) {
            CustomerUserSyncEntity sync = findSync(user, vendor);
            statusList.add(buildVendorStatusDto(vendor, sync));
        }

        return VendorConnectionStatusResponse.builder()
                .customerUserId(user.getId())
                .vendors(statusList)
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private SyncVendorLangEntity resolveLang(SyncVendorEntity vendor, int langId) {
        Optional<SyncVendorLangEntity> lang = syncVendorLangRepository
                .findByVendorAndLanguage(vendor, langId);
        if (lang.isPresent()) return lang.get();

        if (langId != 1) {
            return syncVendorLangRepository.findByVendorAndLanguage(vendor, 1).orElse(null);
        }
        return null;
    }

    private CustomerUserSyncEntity findSync(CustomerUserEntity user, SyncVendorEntity vendor) {
        if (user == null) return null;
        return customerUserSyncRepository
                .findByCustomerUserIdAndSyncVendorId(user.getId(), vendor.getId())
                .orElse(null);
    }

    private boolean isConnected(CustomerUserSyncEntity sync) {
        if (sync == null) return false;
        if (sync.getSyncStatus() == null) return false;
        if (!Integer.valueOf(1).equals(sync.getSyncStatus().getIsActive())) return false;
        if (!"SUCCESS".equals(sync.getSyncStatus().getName())) return false;
        return sync.getAccessToken() != null;
    }

    private VendorDto buildVendorDto(SyncVendorEntity vendor, SyncVendorLangEntity lang,
                                     CustomerUserSyncEntity sync) {
        boolean connected = isConnected(sync);
        return VendorDto.builder()
                .vendorId(vendor.getId())
                .vendorCode(vendor.getName())
                .displayName(lang != null && lang.getDisplayName() != null
                        ? lang.getDisplayName() : vendor.getDisplayName())
                .description(lang != null ? lang.getDescription() : null)
                .logo(lang != null ? lang.getLogo() : null)
                .apiAuthType(resolveApiAuthType(vendor.getApiAuthType()))
                .vendorType(resolveVendorType(vendor.getVendorType()))
                .connected(connected)
                .connectedEmail(connected ? sync.getSyncEmail() : null)
                .isNewConnection(vendor.getIsNewConnection())
                .displaySortOrder(vendor.getDisplaySortOrder())
                .build();
    }

    private VendorStatusDto buildVendorStatusDto(SyncVendorEntity vendor, CustomerUserSyncEntity sync) {
        boolean connected = isConnected(sync);
        if (!connected) {
            return VendorStatusDto.builder()
                    .vendorId(vendor.getId())
                    .vendorCode(vendor.getName())
                    .connected(false)
                    .build();
        }

        long calendarCount = countCalendars(sync);
        boolean webhookActive = hasActiveWebhook(sync);

        return VendorStatusDto.builder()
                .vendorId(vendor.getId())
                .vendorCode(vendor.getName())
                .connected(true)
                .syncStatus(sync.getSyncStatus().getName())
                .connectedEmail(sync.getSyncEmail())
                .lastSyncDate(sync.getLastSyncDate())
                .calendarCount(calendarCount)
                .webhookActive(webhookActive)
                .build();
    }

    private long countCalendars(CustomerUserSyncEntity sync) {
        try {
            return cuSyncCalendarRepository.countByCustomerUserSync(sync);
        } catch (Exception e) {
            log.warn("Could not count calendars for sync id={}: {}", sync.getId(), e.getMessage());
            return 0;
        }
    }

    private boolean hasActiveWebhook(CustomerUserSyncEntity sync) {
        try {
            return webhookRepository.countActiveWebhooksBySync(sync) > 0;
        } catch (Exception e) {
            log.warn("Could not check webhooks for sync id={}: {}", sync.getId(), e.getMessage());
            return false;
        }
    }

    private static String resolveApiAuthType(Integer apiAuthType) {
        if (apiAuthType == null) return "UNKNOWN";
        return switch (apiAuthType) {
            case 1 -> "OAUTH2";
            case 2 -> "API_KEY";
            default -> "UNKNOWN";
        };
    }

    private static String resolveVendorType(Integer vendorType) {
        if (vendorType == null) return "UNKNOWN";
        return switch (vendorType) {
            case 1 -> "CALENDAR";
            case 2 -> "EMAIL";
            default -> "UNKNOWN";
        };
    }
}
