package com.omvrti.calendar_service.oauth.service;

import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.repository.CustomerUserSyncRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TokenRefreshServiceTest {

    @Test
    void getValidAccessToken_refreshesWhenExpired() {
        CustomerUserSyncRepository syncRepository = mock(CustomerUserSyncRepository.class);
        IOAuthProvider googleProvider = mock(IOAuthProvider.class);

        TokenRefreshService service = new TokenRefreshService(
                syncRepository, Map.of(ProviderType.GOOGLE, googleProvider));

        CustomerUserSyncEntity sync = CustomerUserSyncEntity.builder()
                .syncingAccountReference("a@b.com")
                .accessTokenExpiryDate(LocalDateTime.now().minusMinutes(1))
                .build();
        sync.setAccessToken("old");
        sync.setRefreshToken("rt");

        when(googleProvider.refreshAccessToken("rt"))
                .thenReturn(OAuthTokenDto.builder()
                        .accessToken("new").refreshToken("rt").expiresIn(3600L).build());

        CustomerUserSyncEntity saved = CustomerUserSyncEntity.builder()
                .syncingAccountReference("a@b.com")
                .accessTokenExpiryDate(LocalDateTime.now().plusMinutes(55))
                .build();
        saved.setAccessToken("new");
        saved.setRefreshToken("rt");
        when(syncRepository.save(any())).thenReturn(saved);

        String token = service.getValidAccessToken(sync, ProviderType.GOOGLE);
        assertThat(token).isEqualTo("new");

        verify(googleProvider, times(1)).refreshAccessToken("rt");
        verify(syncRepository, atLeastOnce()).save(any(CustomerUserSyncEntity.class));
    }
}
