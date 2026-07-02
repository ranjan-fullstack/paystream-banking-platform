package com.paystream.commonlib.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Logback converter registered as %maskedMsg.
 *
 * Replaces PII in log message text before any appender writes it:
 *   - Email addresses          → [MASKED-EMAIL]
 *   - Indian mobile numbers    → [MASKED-PHONE]
 *   - Payment card numbers     → [MASKED-CARD]
 *   - Aadhaar numbers          → [MASKED-AADHAAR]
 *
 * Wire-up in logback-spring.xml:
 *   <conversionRule conversionWord="maskedMsg"
 *       converterClass="com.paystream.commonlib.logging.PiiMaskingConverter"/>
 *
 * Then use %maskedMsg anywhere %msg / %message would appear in a pattern.
 * For LogstashEncoder (JSON console) use LoggingEventCompositeJsonEncoder
 * with a <pattern> provider so %maskedMsg is applied to the "message" field too.
 *
 * The static mask() method can be called independently (e.g. in tests or
 * from a custom MessageJsonProvider subclass).
 */
public class PiiMaskingConverter extends MessageConverter {

    // Ordered: more specific patterns first to avoid double-masking
    private static final List<MaskRule> RULES = List.of(
            new MaskRule(
                    // Payment cards: 4111-1111-1111-1111 or 4111 1111 1111 1111
                    // Requires a separator so 16-digit IDs (order IDs, product IDs) are NOT masked
                    Pattern.compile("\\b\\d{4}[\\-\\s]\\d{4}[\\-\\s]\\d{4}[\\-\\s]\\d{4}\\b"),
                    "[MASKED-CARD]"
            ),
            new MaskRule(
                    // Aadhaar: 1234 5678 9012 (spaced format only — continuous 12 digits are too
                    // likely to match legitimate IDs like order numbers)
                    Pattern.compile("\\b\\d{4}\\s\\d{4}\\s\\d{4}\\b"),
                    "[MASKED-AADHAAR]"
            ),
            new MaskRule(
                    // Email addresses
                    Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,6}"),
                    "[MASKED-EMAIL]"
            ),
            new MaskRule(
                    // Indian mobile: 9876543210, +91-9876543210, +91 9876543210
                    Pattern.compile("(\\+91[-\\s]?)?[6-9]\\d{9}"),
                    "[MASKED-PHONE]"
            )
    );

    @Override
    public String convert(ILoggingEvent event) {
        return mask(event.getFormattedMessage());
    }

    public static String mask(String message) {
        if (message == null) return null;
        String result = message;
        for (MaskRule rule : RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }

    private record MaskRule(Pattern pattern, String replacement) {}
}
