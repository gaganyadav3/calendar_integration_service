package com.omvrti.calendar_service.persistence.entity;

import com.omvrti.calendar_service.common.enums.ProviderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "OAUTH_TOKENS", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_email", "provider"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthTokenEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String userEmail;
    
    @Column(nullable = false)
    private ProviderType provider;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String accessToken;
    
    @Column(columnDefinition = "TEXT")
    private String refreshToken;
    
    @Column
    private Long expiresIn;
    
    @Column
    private String tokenType;
    
    @Column(columnDefinition = "TEXT")
    private String scope;
    
    @Column
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime updatedAt;
    
    @Column
    private LocalDateTime refreshedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public boolean isExpired() {
        if (refreshedAt == null || expiresIn == null) {
            return true;
        }
        LocalDateTime expirationTime = refreshedAt.plusSeconds(expiresIn);
        // Consider expired 5 minutes before actual expiration for safety margin
        return LocalDateTime.now().isAfter(expirationTime.minusMinutes(5));
    }
}

