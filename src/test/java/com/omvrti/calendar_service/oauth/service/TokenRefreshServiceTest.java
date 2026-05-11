package com.omvrti.calendar_service.oauth.service;

import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import com.omvrti.calendar_service.persistence.entity.UserEntity;
import com.omvrti.calendar_service.persistence.repository.ConnectedAccountRepository;
import com.omvrti.calendar_service.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TokenRefreshServiceTest {

    @Test
    void getValidAccessToken_refreshesWhenExpired() {
        ConnectedAccountRepository accountRepository = mock(ConnectedAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        IOAuthProvider googleProvider = mock(IOAuthProvider.class);

        TokenRefreshService service = new TokenRefreshService(accountRepository, userRepository, Map.of(ProviderType.GOOGLE, googleProvider));

        UserEntity user = UserEntity.builder().id(1L).email("a@b.com").build();
        ConnectedAccountEntity expired = ConnectedAccountEntity.builder()
                .id(10L)
                .user(user)
                .provider(ProviderType.GOOGLE)
                .externalUserId("a@b.com")
                .accessToken("old")
                .refreshToken("rt")
                .accessTokenExpiresAt(LocalDateTime.now().minusMinutes(1))
                .isActive(true)
                .build();

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserAndProvider(user, ProviderType.GOOGLE)).thenReturn(Optional.of(expired));
        when(googleProvider.refreshAccessToken("rt")).thenReturn(OAuthTokenDto.builder().accessToken("new").refreshToken("rt").expiresIn(3600L).build());

        // After refresh, service reloads account; return updated token
        ConnectedAccountEntity refreshed = ConnectedAccountEntity.builder()
                .id(10L)
                .user(user)
                .provider(ProviderType.GOOGLE)
                .externalUserId("a@b.com")
                .accessToken("new")
                .refreshToken("rt")
                .accessTokenExpiresAt(LocalDateTime.now().plusMinutes(30))
                .isActive(true)
                .build();
        when(accountRepository.findByUserAndProvider(user, ProviderType.GOOGLE)).thenReturn(Optional.of(expired), Optional.of(refreshed));

        String token = service.getValidAccessToken("a@b.com", ProviderType.GOOGLE);
        assertThat(token).isEqualTo("new");

        verify(googleProvider, times(1)).refreshAccessToken("rt");
        verify(accountRepository, atLeastOnce()).save(any(ConnectedAccountEntity.class));
    }
}

