package com.omvrti.calendar_service.common.enums;

public enum OAuthScope {
    CALENDAR_READ("calendar"),
    CALENDAR_WRITE("calendar.write"),
    CALENDAR_READONLY("calendar.readonly"),
    OFFLINE_ACCESS("offline_access");

    private final String value;

    OAuthScope(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

