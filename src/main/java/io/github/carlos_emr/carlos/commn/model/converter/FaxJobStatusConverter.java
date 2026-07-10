package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for fax job statuses.
 * Translates between the database representation and the FaxJobStatus enum to maintain type safety in the domain model.
 *
 * @since 2026-07-09
 */

@Converter
public class FaxJobStatusConverter extends NullSafeEnumConverter<STATUS> {
    public FaxJobStatusConverter() {
        super(STATUS.class, null);
    }
}
