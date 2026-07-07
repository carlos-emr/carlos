package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between FaxJobStatus enumeration and its database column representation.
 */
@Converter
public class FaxJobStatusConverter extends NullSafeEnumConverter<STATUS> {
    public FaxJobStatusConverter() {
        // Initialize execution context for FaxJobStatusConverter

        super(STATUS.class, null);
    }
}
