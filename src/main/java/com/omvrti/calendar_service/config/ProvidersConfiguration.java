package com.omvrti.calendar_service.config;

import com.omvrti.calendar_service.calendar.provider.ICalendarProvider;
import com.omvrti.calendar_service.calendar.provider.impl.GoogleCalendarProvider;
import com.omvrti.calendar_service.calendar.provider.impl.OutlookCalendarProvider;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import com.omvrti.calendar_service.oauth.provider.impl.GoogleOAuthProvider;
import com.omvrti.calendar_service.oauth.provider.impl.OutlookOAuthProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for calendar and OAuth providers
 */
@Configuration
public class ProvidersConfiguration {

    /**
     * Register all calendar providers
     */
    @Bean
    public Map<ProviderType, ICalendarProvider> calendarProviders(
            GoogleCalendarProvider googleCalendarProvider,
            OutlookCalendarProvider outlookCalendarProvider) {

        Map<ProviderType, ICalendarProvider> providers = new HashMap<>();
        providers.put(ProviderType.GOOGLE, googleCalendarProvider);
        providers.put(ProviderType.OUTLOOK, outlookCalendarProvider);

        // Uncomment when providers are implemented:
        // providers.put(ProviderType.ZOHO, zohoCalendarProvider);
        // providers.put(ProviderType.APPLE, appleCalendarProvider);
        // providers.put(ProviderType.CALENDLY, calendlyCalendarProvider);
        // providers.put(ProviderType.THUNDERBIRD, thunderbirdCalendarProvider);

        return providers;
    }

    /**
     * Register all OAuth providers
     */
    @Bean
    public Map<ProviderType, IOAuthProvider> oauthProviders(
            GoogleOAuthProvider googleOAuthProvider,
            OutlookOAuthProvider outlookOAuthProvider) {

        Map<ProviderType, IOAuthProvider> providers = new HashMap<>();
        providers.put(ProviderType.GOOGLE, googleOAuthProvider);
        providers.put(ProviderType.OUTLOOK, outlookOAuthProvider);

        // Uncomment when providers are implemented:
        // providers.put(ProviderType.ZOHO, zohoOAuthProvider);
        // providers.put(ProviderType.APPLE, appleOAuthProvider);
        // providers.put(ProviderType.CALENDLY, calendlyOAuthProvider);
        // providers.put(ProviderType.THUNDERBIRD, thunderbirdOAuthProvider);

        return providers;
    }

    /**
     * Configure RestTemplate for HTTP requests
     */
    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setConnectionRequestTimeout(5000);
        return new RestTemplate(factory);
    }
}


