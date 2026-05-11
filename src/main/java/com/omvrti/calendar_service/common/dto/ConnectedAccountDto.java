package com.omvrti.calendar_service.common.dto;

import com.omvrti.calendar_service.common.enums.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectedAccountDto {
    
    private Long id;
    private ProviderType provider;
    private String externalUserId;
    private boolean isActive;
    private LocalDateTime connectedAt;
    private LocalDateTime lastTokenRefreshAt;
    private String scope;
}

