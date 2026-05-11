package com.omvrti.calendar_service.common.enums;

import java.util.Locale;

public enum ProviderType {
    GOOGLE("google", "https://www.googleapis.com/oauth2/v4/token"),
    OUTLOOK("outlook", "https://login.microsoftonline.com/common/oauth2/v2.0/token"),
    ZOHO("zoho", "https://accounts.zoho.com/oauth/v2/token"),
    APPLE("apple", "https://appleid.apple.com/auth/oauth2/token"),
    CALENDLY("calendly", "https://auth.calendly.com/oauth/token"),
    THUNDERBIRD("thunderbird", "");

    private final String displayName;
    private final String tokenEndpoint;

    ProviderType(String displayName, String tokenEndpoint) {
        this.displayName = displayName;
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public static ProviderType parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Provider cannot be null");
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Provider cannot be blank");
        }

        for (ProviderType providerType : values()) {
            if (providerType.name().equalsIgnoreCase(normalized)
                    || providerType.displayName.equalsIgnoreCase(normalized)) {
                return providerType;
            }
        }

        throw new IllegalArgumentException("Invalid provider: " + value);
    }

    public String toDatabaseValue() {
        return name().toUpperCase(Locale.ROOT);
    }
}

