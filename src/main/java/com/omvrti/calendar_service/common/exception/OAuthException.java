package com.omvrti.calendar_service.common.exception;

public class OAuthException extends RuntimeException {
    private String errorCode;
    private String providerName;

    public OAuthException(String message) {
        super(message);
    }

    public OAuthException(String message, Throwable cause) {
        super(message, cause);
    }

    public OAuthException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OAuthException(String errorCode, String message, String providerName) {
        super(message);
        this.errorCode = errorCode;
        this.providerName = providerName;
    }

    public OAuthException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public OAuthException(String tokenExchangeFailed, String s, String google, Exception e) {
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getProviderName() {
        return providerName;
    }
}

