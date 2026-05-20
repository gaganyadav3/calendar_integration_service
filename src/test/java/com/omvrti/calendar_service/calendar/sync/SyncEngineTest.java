package com.omvrti.calendar_service.calendar.sync;

import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.calendar.service.ProviderCalendarService;
import com.omvrti.calendar_service.calendar.service.ProviderEventService;
import com.omvrti.calendar_service.common.dto.EventDto;
import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.util.EventEntityMapper;
import com.omvrti.calendar_service.persistence.entity.*;
import com.omvrti.calendar_service.persistence.repository.CUSyncCalendarEventRepository;
import com.omvrti.calendar_service.persistence.repository.CustomerUserRepository;
import com.omvrti.calendar_service.persistence.repository.CustomerUserSyncRepository;
import com.omvrti.calendar_service.persistence.service.SyncStatusService;
import com.omvrti.calendar_service.persistence.service.SyncVendorService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SyncEngineTest {

    @Test
    void sync_fetchesAndPersistsRemoteEvents() {
        CustomerUserRepository customerUserRepository = mock(CustomerUserRepository.class);
        CustomerUserSyncRepository customerUserSyncRepository = mock(CustomerUserSyncRepository.class);
        CUSyncCalendarEventRepository eventRepository = mock(CUSyncCalendarEventRepository.class);
        ProviderCalendarService providerCalendarService = mock(ProviderCalendarService.class);
        ProviderEventService providerEventService = mock(ProviderEventService.class);
        SyncVendorService syncVendorService = mock(SyncVendorService.class);
        SyncStatusService syncStatusService = mock(SyncStatusService.class);
        EventEntityMapper eventMapper = mock(EventEntityMapper.class);
        ICalendarProvider provider = mock(ICalendarProvider.class);

        SyncEngine engine = new SyncEngine(
                customerUserRepository,
                customerUserSyncRepository,
                eventRepository,
                providerCalendarService,
                providerEventService,
                syncVendorService,
                syncStatusService,
                eventMapper,
                Map.of(ProviderType.GOOGLE, provider)
        );

        CustomerUserEntity customerUser = CustomerUserEntity.builder()
                .email("u@example.com").build();

        SyncVendorEntity vendor = SyncVendorEntity.builder()
                .name("GOOGLE")
                .displayName("Google Calendar")
                .apiAuthType(1).vendorType(1).isNewConnection(0).isValid(1).displaySortOrder(0)
                .build();

        CustomerUserSyncEntity sync = CustomerUserSyncEntity.builder()
                .customerUser(customerUser)
                .syncVendor(vendor)
                .syncingAccountReference("u@example.com")
                .accessTokenExpiryDate(LocalDateTime.now().plusHours(1))
                .build();
        sync.setAccessToken("tok");

        CUSyncCalendarEntity calendar = CUSyncCalendarEntity.builder()
                .customerUserSync(sync)
                .calendarReference("primary")
                .isEnabled(1)
                .build();

        when(customerUserRepository.findByEmail("u@example.com")).thenReturn(Optional.of(customerUser));
        when(syncVendorService.getOrCreateVendor(ProviderType.GOOGLE)).thenReturn(vendor);
        when(customerUserSyncRepository.findByCustomerUserAndSyncVendor(customerUser, vendor))
                .thenReturn(Optional.of(sync));
        when(providerCalendarService.getEnabledCalendars(sync)).thenReturn(List.of(calendar));
        when(syncStatusService.findByCode(anyString())).thenReturn(Optional.empty());

        EventDto remoteEvent = EventDto.builder()
                .externalId("g1")
                .title("Remote Event")
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .endTime(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                .externalUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .source(EventSource.GOOGLE)
                .provider(ProviderType.GOOGLE)
                .build();

        when(provider.fetchEventsWithToken(eq(sync), eq("primary"), isNull()))
                .thenReturn(new ICalendarProvider.SyncFetchResult(List.of(remoteEvent), "nextToken"));
        when(provider.fetchCalendars(sync)).thenReturn(List.of());

        when(eventRepository.findByCuSyncCalendarAndCalendarEventReference(calendar, "g1"))
                .thenReturn(Optional.empty());

        CUSyncCalendarEventEntity savedEvent = CUSyncCalendarEventEntity.builder()
                .cuSyncCalendar(calendar)
                .calendarEventReference("g1")
                .build();
        when(eventRepository.save(any())).thenReturn(savedEvent);
        when(customerUserSyncRepository.save(any())).thenReturn(sync);

        SyncEngine.SyncResult result = engine.sync("u@example.com", ProviderType.GOOGLE);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals(1, result.getFetchedRemoteCount());
        verify(eventRepository, atLeastOnce()).save(any());
        verify(providerCalendarService).updateSyncCursor(calendar);
    }
}
