package com.omvrti.calendar_service.calendar.service;

import com.omvrti.calendar_service.persistence.entity.CUSyncCalendarEntity;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.repository.CUSyncCalendarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderCalendarService {

    private final CUSyncCalendarRepository calendarRepository;

    @Transactional
    public CUSyncCalendarEntity createOrUpdateCalendar(
            CustomerUserSyncEntity customerUserSync,
            String calendarReference,
            String displayName,
            String color,
            String timeZone,
            Boolean isPrimary,
            Boolean isWritable) {

        log.debug("Creating/updating calendar: {} - {}", customerUserSync.getSyncEmail(), calendarReference);

        Optional<CUSyncCalendarEntity> existing = calendarRepository
                .findByCustomerUserSyncAndCalendarReference(customerUserSync, calendarReference);

        CUSyncCalendarEntity calendar = existing.orElse(CUSyncCalendarEntity.builder()
                .customerUserSync(customerUserSync)
                .calendarReference(calendarReference)
                .isEnabled(1)
                .build());

        calendar.setDisplayName(displayName);
        calendar.setColor(color);
        calendar.setTimeZone(timeZone);
        calendar.setIsPrimary(isPrimary != null && isPrimary ? 1 : 0);
        calendar.setIsWritable(isWritable != null ? isWritable : false);

        return calendarRepository.save(calendar);
    }

    public Optional<CUSyncCalendarEntity> getCalendarByReference(
            CustomerUserSyncEntity customerUserSync, String calendarReference) {
        return calendarRepository.findByCustomerUserSyncAndCalendarReference(customerUserSync, calendarReference);
    }

    public List<CUSyncCalendarEntity> getCalendars(CustomerUserSyncEntity customerUserSync) {
        return calendarRepository.findByCustomerUserSync(customerUserSync);
    }

    public List<CUSyncCalendarEntity> getEnabledCalendars(CustomerUserSyncEntity customerUserSync) {
        return calendarRepository.findByCustomerUserSyncAndIsSyncOnTrue(customerUserSync);
    }

    public Optional<CUSyncCalendarEntity> getPrimaryCalendar(CustomerUserSyncEntity customerUserSync) {
        return calendarRepository.findByCustomerUserSyncAndIsPrimaryTrue(customerUserSync)
                .stream().findFirst();
    }

    @Transactional
    public void enableSync(CUSyncCalendarEntity calendar) {
        calendar.setIsSyncOn(true);
        calendarRepository.save(calendar);
    }

    @Transactional
    public void disableSync(CUSyncCalendarEntity calendar) {
        calendar.setIsSyncOn(false);
        calendarRepository.save(calendar);
    }

    /**
     * Mark the sync timestamp for a calendar — new schema stores LocalDateTime, not a string token.
     */
    @Transactional
    public void updateSyncCursor(CUSyncCalendarEntity calendar) {
        log.debug("Updating sync cursor for calendar: {}", calendar.getCalendarReference());
        calendar.setSyncCursor(LocalDateTime.now());
        calendarRepository.save(calendar);
    }

    @Transactional
    public CUSyncCalendarEntity getOrCreatePrimaryCalendar(CustomerUserSyncEntity customerUserSync, String calendarId) {
        if (calendarId != null && !"primary".equals(calendarId)) {
            return calendarRepository.findByCustomerUserSyncAndCalendarReference(customerUserSync, calendarId)
                    .orElseGet(() -> createOrUpdateCalendar(customerUserSync, calendarId, "Primary",
                            null, null, true, true));
        }
        return calendarRepository.findByCustomerUserSyncAndIsPrimaryTrue(customerUserSync)
                .stream().findFirst()
                .orElseGet(() -> createOrUpdateCalendar(customerUserSync, "primary", "Primary",
                        null, null, true, true));
    }

    @Transactional
    public void deleteCalendar(CUSyncCalendarEntity calendar) {
        calendarRepository.delete(calendar);
    }
}
