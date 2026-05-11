package com.omvrti.calendar_service.oauth.factory;

import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.common.exception.OAuthException;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Factory for creating OAuth provider instances
 */
@Component
@RequiredArgsConstructor
public class OAuthProviderFactory {
    
    private final Map<ProviderType, IOAuthProvider> providers;
    
    public IOAuthProvider getProvider(ProviderType providerType) {
        IOAuthProvider provider = providers.get(providerType);
        if (provider == null) {
            throw new OAuthException(
                "PROVIDER_NOT_FOUND",
                "OAuth provider not found for type: " + providerType
            );
        }
        return provider;
    }
}

