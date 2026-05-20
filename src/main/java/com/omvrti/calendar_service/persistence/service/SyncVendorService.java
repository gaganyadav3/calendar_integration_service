package com.omvrti.calendar_service.persistence.service;

import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.persistence.entity.SyncVendorEntity;
import com.omvrti.calendar_service.persistence.repository.SyncVendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncVendorService {

    private final SyncVendorRepository syncVendorRepository;
    private final Map<String, SyncVendorEntity> vendorCache = new ConcurrentHashMap<>();

    @Transactional
    public SyncVendorEntity getOrCreateVendor(ProviderType providerType) {
        String vendorName = providerType.name();
        if (vendorCache.containsKey(vendorName)) return vendorCache.get(vendorName);

        SyncVendorEntity vendor = syncVendorRepository.findByVendorCode(vendorName)
                .orElseGet(() -> {
                    SyncVendorEntity v = SyncVendorEntity.builder()
                            .name(vendorName)
                            .displayName(providerType.getDisplayName())
                            .apiAuthType(1)
                            .vendorType(1)
                            .isNewConnection(0)
                            .isValid(1)
                            .displaySortOrder(1)
                            .build();
                    return syncVendorRepository.save(v);
                });

        vendorCache.put(vendorName, vendor);
        return vendor;
    }

    public SyncVendorEntity getVendor(ProviderType providerType) {
        return syncVendorRepository.findByVendorCode(providerType.name())
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + providerType.name()));
    }

    public ProviderType getProviderType(SyncVendorEntity vendor) {
        return ProviderType.valueOf(vendor.getVendorCode());
    }
}
