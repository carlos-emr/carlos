package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the FaxJobStatus enum to its database column.
 * Ensures that FaxJobStatus values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class FaxJobStatusConverter extends NullSafeEnumConverter<STATUS> {
    public FaxJobStatusConverter() {
        // Initialize the converter mapping to the target enum class
        super(STATUS.class, null);
    }
}
