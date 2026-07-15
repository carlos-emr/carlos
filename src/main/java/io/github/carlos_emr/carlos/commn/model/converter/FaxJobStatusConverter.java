package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import jakarta.persistence.Converter;
/**
 * JPA AttributeConverter for tracking the state of fax transmission jobs (e.g., queued, sent, failed).
 */

@Converter
public class FaxJobStatusConverter extends NullSafeEnumConverter<STATUS> {
    public FaxJobStatusConverter() {
        super(STATUS.class, null);
    }
}
