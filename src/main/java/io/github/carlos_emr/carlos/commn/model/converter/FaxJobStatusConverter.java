package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for fax transmission statuses.
 *
 * <p>Translates between the internal enum (e.g., QUEUED, SENT, FAILED)
 * and the database representation.</p>
 */

@Converter
public class FaxJobStatusConverter extends NullSafeEnumConverter<STATUS> {
    // Unmapped database values should default to FAILED to ensure issues are reviewed
    public FaxJobStatusConverter() {
        super(STATUS.class, null);
    }
}
