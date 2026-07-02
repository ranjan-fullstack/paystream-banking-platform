package com.paystream.rtgsservice.util;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * RBI RTGS window: Monday-Friday 7:00 AM-5:45 PM, Saturday 7:00 AM-1:45 PM.
 * Source: RBI Master Direction on RTGS System (updated 2021).
 */
@Component
public class RtgsWindowValidator {

    private static final LocalTime WEEKDAY_OPEN = LocalTime.of(7, 0);
    private static final LocalTime WEEKDAY_CLOSE = LocalTime.of(17, 45);
    private static final LocalTime SATURDAY_OPEN = LocalTime.of(7, 0);
    private static final LocalTime SATURDAY_CLOSE = LocalTime.of(13, 45);

    public boolean isWithinWindow(LocalDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        if (day == DayOfWeek.SUNDAY) {
            return false;
        }
        if (day == DayOfWeek.SATURDAY) {
            return !time.isBefore(SATURDAY_OPEN) && !time.isAfter(SATURDAY_CLOSE);
        }
        return !time.isBefore(WEEKDAY_OPEN) && !time.isAfter(WEEKDAY_CLOSE);
    }
}
