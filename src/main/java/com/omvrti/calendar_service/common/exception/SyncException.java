package com.omvrti.calendar_service.common.exception;

public class SyncException extends RuntimeException {
    private String errorCode;
    
    public SyncException(String message) {
        super(message);
    }
    
    public SyncException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public SyncException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public SyncException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

