package io.github.carlos_emr.carlos.lab.ca.all.util;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ca.uhn.hl7v2.HL7Exception;

/**
 * Helpers for reading HAPI HL7 v2 messages.
 *
 * <p>HAPI 2.x no longer declares the checked {@link HL7Exception} on its generated accessors.
 * A repetition or field that is not there (for example {@code getORDER_OBSERVATION(5)} on a
 * message with two) now surfaces as a {@link RuntimeException} whose cause is the
 * {@link HL7Exception}. The lab handlers answer such a lookup with an empty or default value, so
 * code that used to catch {@link HL7Exception} catches {@link RuntimeException} and calls
 * {@link #rethrowUnlessHl7AccessFailure(RuntimeException)} to keep every other runtime failure
 * visible.
 */
public final class Hl7Utils {

    private static final Logger logger = LoggerFactory.getLogger(Hl7Utils.class);

    private Hl7Utils() {} // utility class

    public static String safeHl7String(CheckedSupplier<String> s) {
        try {
            return Optional.ofNullable(s.get()).map(String::trim).orElse("");
        } catch (HL7Exception e) {
            logger.warn("HL7 access error: {}", e.getMessage());
            return "";
        } catch (Exception e) {
            logger.warn("Unexpected error: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Tells whether {@code t} is HAPI reporting that a repetition, field or component is not in
     * the message.
     *
     * @param t the failure caught around an HAPI accessor
     * @return true for an {@link HL7Exception} or a {@link RuntimeException} caused by one
     */
    public static boolean isHl7AccessFailure(Throwable t) {
        return t instanceof HL7Exception
                || (t instanceof RuntimeException && t.getCause() instanceof HL7Exception);
    }

    /**
     * Rethrows {@code e} unless it is an HAPI access failure, so a handler's empty-value fallback
     * covers what it covered with HAPI 1.x and does not hide programming errors.
     *
     * @param e the runtime failure caught around an HAPI accessor
     */
    public static void rethrowUnlessHl7AccessFailure(RuntimeException e) {
        if (!isHl7AccessFailure(e)) {
            throw e;
        }
    }

    /**
     * Gives back the checked {@link HL7Exception} that HAPI 2.x wraps in a {@link RuntimeException},
     * for public handler methods whose callers were written against {@code throws HL7Exception}.
     *
     * @param e the runtime failure caught around an HAPI accessor
     * @return the wrapped {@link HL7Exception}, ready to be thrown
     * @throws RuntimeException {@code e} itself when it is not an HAPI access failure
     */
    public static HL7Exception unwrapHl7AccessFailure(RuntimeException e) {
        if (e.getCause() instanceof HL7Exception hl7Exception) {
            return hl7Exception;
        }
        throw e;
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}

