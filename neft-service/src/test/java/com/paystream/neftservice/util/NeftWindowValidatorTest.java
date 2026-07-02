package com.paystream.neftservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NEFT is 24x7 per RBI circular RBI/2019-20/125 (Dec 2019).
 * All times and days must be accepted.
 */
@DisplayName("NeftWindowValidator unit tests")
class NeftWindowValidatorTest {

    private final NeftWindowValidator validator = new NeftWindowValidator();

    @Test
    @DisplayName("Should allow NEFT transfer on Monday morning")
    void testIsWithinWindow_MondayMorning_returnsTrue() {
        assertThat(validator.isWithinWindow(LocalDateTime.of(2026, 6, 29, 10, 0))).isTrue();
    }

    @Test
    @DisplayName("Should allow NEFT transfer on Sunday (24x7 per RBI/2019-20/125)")
    void testIsWithinWindow_Sunday_returnsTrue() {
        assertThat(validator.isWithinWindow(LocalDateTime.of(2026, 6, 28, 12, 0))).isTrue();
    }

    @Test
    @DisplayName("Should allow NEFT transfer before 8:00 AM (24x7 per RBI/2019-20/125)")
    void testIsWithinWindow_Before8AM_returnsTrue() {
        assertThat(validator.isWithinWindow(LocalDateTime.of(2026, 6, 29, 7, 59))).isTrue();
    }

    @Test
    @DisplayName("Should allow NEFT transfer after 7:00 PM (24x7 per RBI/2019-20/125)")
    void testIsWithinWindow_After7PM_returnsTrue() {
        assertThat(validator.isWithinWindow(LocalDateTime.of(2026, 6, 29, 19, 1))).isTrue();
    }

    @Test
    @DisplayName("Should allow NEFT transfer on Saturday at 8:00 AM")
    void testIsWithinWindow_Saturday8AM_returnsTrue() {
        assertThat(validator.isWithinWindow(LocalDateTime.of(2026, 7, 4, 8, 0))).isTrue();
    }

    @Test
    @DisplayName("Should allow NEFT transfer on Saturday late evening (24x7 per RBI/2019-20/125)")
    void testIsWithinWindow_SaturdayLateEvening_returnsTrue() {
        assertThat(validator.isWithinWindow(LocalDateTime.of(2026, 7, 4, 23, 30))).isTrue();
    }
}
