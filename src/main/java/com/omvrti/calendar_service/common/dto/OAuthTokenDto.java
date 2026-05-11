package com.omvrti.calendar_service.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthTokenDto {
    private String accessToken;
    private String refreshToken;
    private String idToken;
    private Long expiresIn;
    private String tokenType;
    private String scope;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;

    public boolean isExpired() {
        if (expiresAt == null) {
            return true;
        }
        // Consider expired if within 5 minutes of expiration
        return LocalDateTime.now().isAfter(expiresAt.minusMinutes(5));
    }

    public LocalDateTime calculateExpiryTime() {
        if (expiresIn != null) {
            return LocalDateTime.now().plusSeconds(expiresIn);
        }
        return null;
    }
}
