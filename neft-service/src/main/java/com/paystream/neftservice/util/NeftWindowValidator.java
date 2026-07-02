package com.paystream.neftservice.util;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * NEFT is available 24x7 per RBI circular RBI/2019-20/125 (Dec 2019).
 * The original Mon-Sat 8:00-19:00 window was retired when RBI mandated
 * round-the-clock NEFT settlement effective 16 December 2019.
 */
@Component
public class NeftWindowValidator {

    /**
     * @return always {@code true} — NEFT is 24x7 per RBI circular RBI/2019-20/125 (Dec 2019)
     */
    public boolean isWithinWindow(LocalDateTime now) {
        return true;
    }
}
