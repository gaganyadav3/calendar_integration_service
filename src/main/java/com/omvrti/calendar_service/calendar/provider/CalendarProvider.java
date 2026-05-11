package com.omvrti.calendar_service.calendar.provider;

/**
 * Alias for the unified calendar provider abstraction.
 *
 * The codebase originally introduced {@link ICalendarProvider}; this type keeps the
 * user-facing contract name ("CalendarProvider") while staying source-compatible.
 */
public interface CalendarProvider extends ICalendarProvider {}

