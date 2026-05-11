package com.omvrti.calendar_service.calendar.factory;

import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.CalendarException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Factory for creating calendar provider instances
 */
@Component
@RequiredArgsConstructor
public class CalendarProviderFactory {
    
    private final Map<ProviderType, ICalendarProvider> providers;
    
    public ICalendarProvider getProvider(ProviderType providerType) {
        ICalendarProvider provider = providers.get(providerType);
        if (provider == null) {
            throw new CalendarException(
                "PROVIDER_NOT_FOUND",
                "Calendar provider not found for type: " + providerType
            );
    }
        return provider;
    }
}

