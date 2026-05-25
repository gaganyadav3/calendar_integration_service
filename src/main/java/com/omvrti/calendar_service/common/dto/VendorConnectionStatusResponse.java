package com.omvrti.calendar_service.common.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorConnectionStatusResponse {
    private Long customerUserId;
    private List<VendorStatusDto> vendors;
}
