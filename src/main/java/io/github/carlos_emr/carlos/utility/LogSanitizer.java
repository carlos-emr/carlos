package io.github.carlos_emr.carlos.utility;

/**
 * Backward-compatible logging sanitizer alias retained for callers that have not yet migrated
 * to {@link LogSafe}.
 *
 * @deprecated use {@link LogSafe} instead.
 */
@Deprecated
public final class LogSanitizer {

    private LogSanitizer() {
        // utility class
    }

    public static String sanitize(String input) {
        return LogSafe.sanitize(input);
    }

    public static String sanitize(String input, int maxLength) {
        return LogSafe.sanitize(input, maxLength);
    }
}
