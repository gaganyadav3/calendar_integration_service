package com.omvrti.calendar_service.persistence.entity;

import com.omvrti.calendar_service.common.enums.ProviderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Stores OAuth credentials and account information for connected calendar providers
 */
@Entity
@Table(name = "connected_accounts", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "provider"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectedAccountEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    
    @Column(nullable = false)
    private ProviderType provider;
    
    @Column(nullable = false)
    private String externalUserId;
    
    @Column(columnDefinition = "TEXT")
    private String accessToken;
    
    @Column(columnDefinition = "TEXT")
    private String refreshToken;
    
    @Column(columnDefinition = "TEXT")
    private String idToken;

    @Column
    private LocalDateTime accessTokenExpiresAt;
    
    @Column
    private String scope;
    
    @Column
    private boolean isActive;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime connectedAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column
    private LocalDateTime lastTokenRefreshAt;
    
    public boolean isTokenExpired() {
        if (accessTokenExpiresAt == null) {
            return true;
        }
        // Consider expired if within 5 minutes of expiration
        return LocalDateTime.now().isAfter(accessTokenExpiresAt.minusMinutes(5));
    }
}
