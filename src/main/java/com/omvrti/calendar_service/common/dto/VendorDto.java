package com.omvrti.calendar_service.common.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorDto {
    private Long vendorId;
    private String vendorCode;
    private String displayName;
    private String description;
    private String logo;
    private String apiAuthType;
    private String vendorType;
    private boolean connected;
    private String connectedEmail;
    private Integer isNewConnection;
    private Integer displaySortOrder;
}
