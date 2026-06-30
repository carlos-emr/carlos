package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for fax job states.
 * Converts enum states (e.g. QUEUED, SENT, FAILED) into their persistence codes.
 */
@Converter
public class FaxJobStatusConverter extends NullSafeEnumConverter<STATUS> {
    public FaxJobStatusConverter() {
        // Map active transmission states to single-character DB codes for the legacy schema
        super(STATUS.class, null);
    }
}
