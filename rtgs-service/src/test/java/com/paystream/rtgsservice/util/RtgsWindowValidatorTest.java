package com.paystream.rtgsservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RtgsWindowValidator unit tests")
class RtgsWindowValidatorTest {

    private final RtgsWindowValidator validator = new RtgsWindowValidator();

    @Test
    @DisplayName("Should allow RTGS transfer on a weekday within the 7:00 AM-5:45 PM window")
    void testWeekday_withinWindow_returnsTrue() {
        LocalDateTime tuesdayMorning = LocalDateTime.of(2026, 6, 30, 10, 0); // a Tuesday
        assertThat(validator.isWithinWindow(tuesdayMorning)).isTrue();
    }

    @Test
    @DisplayName("Should disallow RTGS transfer on a weekday before 7:00 AM")
    void testWeekday_before7AM_returnsFalse() {
        LocalDateTime beforeOpen = LocalDateTime.of(2026, 6, 30, 6, 59); // Tuesday 6:59 AM
        assertThat(validator.isWithinWindow(beforeOpen)).isFalse();
    }

    @Test
    @DisplayName("Should disallow RTGS transfer on a weekday after 5:45 PM")
    void testWeekday_after545PM_returnsFalse() {
        LocalDateTime afterClose = LocalDateTime.of(2026, 6, 30, 17, 46); // Tuesday 5:46 PM
        assertThat(validator.isWithinWindow(afterClose)).isFalse();
    }

    @Test
    @DisplayName("Should allow RTGS transfer on a weekday at exactly 5:45 PM (boundary)")
    void testWeekday_at545PM_returnsTrue() {
        LocalDateTime atClose = LocalDateTime.of(2026, 6, 30, 17, 45); // Tuesday 5:45 PM
        assertThat(validator.isWithinWindow(atClose)).isTrue();
    }

    @Test
    @DisplayName("Should allow RTGS transfer on Saturday before 1:45 PM")
    void testSaturday_before145PM_returnsTrue() {
        LocalDateTime saturdayMorning = LocalDateTime.of(2026, 7, 4, 9, 0); // a Saturday
        assertThat(validator.isWithinWindow(saturdayMorning)).isTrue();
    }

    @Test
    @DisplayName("Should allow RTGS transfer on Saturday at 1:01 PM (within extended window to 1:45 PM)")
    void testSaturday_at1PM_withinExtendedWindow_returnsTrue() {
        LocalDateTime saturdayAfternoon = LocalDateTime.of(2026, 7, 4, 13, 1); // Saturday 1:01 PM
        assertThat(validator.isWithinWindow(saturdayAfternoon)).isTrue();
    }

    @Test
    @DisplayName("Should disallow RTGS transfer on Saturday after 1:45 PM")
    void testSaturday_after145PM_returnsFalse() {
        LocalDateTime saturdayAfternoon = LocalDateTime.of(2026, 7, 4, 13, 46); // Saturday 1:46 PM
        assertThat(validator.isWithinWindow(saturdayAfternoon)).isFalse();
    }

    @Test
    @DisplayName("Should disallow RTGS transfer on Sunday regardless of time")
    void testSunday_returnsFalse() {
        LocalDateTime sundayNoon = LocalDateTime.of(2026, 6, 28, 12, 0); // a Sunday
        assertThat(validator.isWithinWindow(sundayNoon)).isFalse();
    }
}
