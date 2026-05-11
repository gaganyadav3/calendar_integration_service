package com.omvrti.calendar_service.calendar.sync;

import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.calendar.service.EventManagementService;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import com.omvrti.calendar_service.persistence.entity.EventEntity;
import com.omvrti.calendar_service.persistence.entity.SyncMetadataEntity;
import com.omvrti.calendar_service.persistence.entity.UserEntity;
import com.omvrti.calendar_service.persistence.repository.ConnectedAccountRepository;
import com.omvrti.calendar_service.persistence.repository.EventRepository;
import com.omvrti.calendar_service.persistence.repository.SyncMetadataRepository;
import com.omvrti.calendar_service.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SyncEngineTest {

    @Test
    void sync_pullsRemoteAndPushesOnlyInternal() {
        ConnectedAccountRepository accountRepository = mock(ConnectedAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        SyncMetadataRepository metadataRepository = mock(SyncMetadataRepository.class);
        EventManagementService eventManagementService = mock(EventManagementService.class);
        TokenRefreshService tokenRefreshService = mock(TokenRefreshService.class);
        ICalendarProvider provider = mock(ICalendarProvider.class);

        SyncEngine engine = new SyncEngine(
                accountRepository,
                userRepository,
                eventRepository,
                metadataRepository,
                eventManagementService,
                tokenRefreshService,
                Map.of(ProviderType.GOOGLE, provider)
        );

        UserEntity user = UserEntity.builder().id(1L).email("u@example.com").build();
        ConnectedAccountEntity account = ConnectedAccountEntity.builder()
                .id(2L)
                .user(user)
                .provider(ProviderType.GOOGLE)
                .externalUserId("u@example.com")
                .isActive(true)
                .accessToken("t")
                .build();

        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserAndProvider(user, ProviderType.GOOGLE)).thenReturn(Optional.of(account));
        when(metadataRepository.findByUserAndProvider(user, ProviderType.GOOGLE)).thenReturn(Optional.of(
                SyncMetadataEntity.builder().user(user).provider(ProviderType.GOOGLE).lastSyncTime(LocalDateTime.now().minusMinutes(10)).build()
        ));

        when(provider.fetchEvents(eq(account), any())).thenReturn(List.of(
                EventDto.builder().externalId("g1").title("remote").startTime(OffsetDateTime.now(ZoneOffset.UTC)).endTime(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1)).externalUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC)).build()
        ));
        when(eventRepository.findByExternalIdAndProvider(anyString(), any())).thenReturn(List.of());

        EventEntity internalChange = EventEntity.builder()
                .id(10L)
                .user(user)
                .provider(ProviderType.GOOGLE)
                .internalId("i1")
                .title("local")
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .endTime(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                .source(EventSource.INTERNAL)
                .isDeleted(false)
                .version(1L)
                .build();

        EventEntity providerSourcedChange = EventEntity.builder()
                .id(11L)
                .user(user)
                .provider(ProviderType.GOOGLE)
                .internalId("i2")
                .title("remote-local")
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .endTime(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                .source(EventSource.GOOGLE)
                .isDeleted(false)
                .version(1L)
                .build();

        when(eventRepository.findByUserAndProviderAndUpdatedAtAfter(eq(user), eq(ProviderType.GOOGLE), any()))
                .thenReturn(List.of(internalChange, providerSourcedChange));

        engine.sync("u@example.com", ProviderType.GOOGLE);

        verify(eventManagementService, times(1)).saveEvent(eq("u@example.com"), any(EventDto.class), eq(account));
        verify(provider, times(1)).createEvent(eq(account), any(EventDto.class));
        verify(provider, never()).updateEvent(eq(account), anyString(), any(EventDto.class));
    }
}
