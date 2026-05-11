package com.omvrti.calendar_service.common.exception;

public class ProviderAuthException extends RuntimeException {
    private String provider;

    public ProviderAuthException(String message) {
        super(message);
    }

    public ProviderAuthException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProviderAuthException(String provider, String message) {
        super(message);
        this.provider = provider;
    }

    public ProviderAuthException(String provider, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}

