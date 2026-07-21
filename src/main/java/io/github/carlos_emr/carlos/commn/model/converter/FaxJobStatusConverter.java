package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the fax job status enumeration.
 * Translates the application-level status Enum to its persistent database
 * format, ensuring data integrity for fax queue processing.
 */
@Converter
public class FaxJobStatusConverter extends NullSafeEnumConverter<STATUS> {
    public FaxJobStatusConverter() {
        super(STATUS.class, null);
    }
}
