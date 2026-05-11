package com.omvrti.calendar_service.common.exception;

public class CalendarException extends RuntimeException {
    private String errorCode;

    public CalendarException(String message) {
        super(message);
    }

    public CalendarException(String message, Throwable cause) {
        super(message, cause);
    }

    public CalendarException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CalendarException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

