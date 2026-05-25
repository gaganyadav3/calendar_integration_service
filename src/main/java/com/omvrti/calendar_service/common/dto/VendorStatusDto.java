package com.omvrti.calendar_service.common.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorStatusDto {
    private Long vendorId;
    private String vendorCode;
    private boolean connected;
    private String syncStatus;
    private String connectedEmail;
    private LocalDateTime lastSyncDate;
    private long calendarCount;
    private boolean webhookActive;
}
