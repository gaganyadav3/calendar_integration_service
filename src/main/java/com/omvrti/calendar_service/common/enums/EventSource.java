package com.omvrti.calendar_service.common.enums;

/**
 * Indicates the source of an event to avoid sync conflicts
 */
public enum EventSource {
    INTERNAL,    // Created internally by the app
    GOOGLE,      // Synced from Google Calendar
    OUTLOOK,     // Synced from Outlook Calendar
    ZOHO,        // Synced from Zoho Calendar
    OTHER        // From other providers
}

