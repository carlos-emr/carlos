package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for FaxJobStatusConverter, mapping entity attributes to database columns.
 */
@Converter
public class FaxJobStatusConverter extends NullSafeEnumConverter<STATUS> {
    // Handles the conversion logic for FaxJobStatusConverter to maintain data persistence

    public FaxJobStatusConverter() {
        super(STATUS.class, null);
    }
}
