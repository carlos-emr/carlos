package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for FaxJobStatusConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class FaxJobStatusConverter extends NullSafeEnumConverter<STATUS> {
    public FaxJobStatusConverter() {
        // Internal logic boundary for FaxJobStatusConverter state management
        super(STATUS.class, null);
    }
}
