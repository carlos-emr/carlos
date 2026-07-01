package io.github.carlos_emr.carlos.lab.ca.all.util;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ca.uhn.hl7v2.HL7Exception;
/**
 * Utility class for parsing and generating HL7 messages.
 * <p>
 * Provides essential helper methods for processing HL7 laboratory and clinical
 * data standards within the CARLOS EMR system.
 * </p>
 */


public final class Hl7Utils {

    private static final Logger logger = LoggerFactory.getLogger(Hl7Utils.class);

    private Hl7Utils() {
    } // utility class

    public static String safeHl7String(CheckedSupplier<String> s) {
        // Parse HL7 segment fields carefully to avoid out-of-bounds errors on malformed lab messages.
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

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}

