package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for fax job status.
 * <p>
 * Ensures seamless conversion between the {@code FaxJobStatus} enum and its
 * database representation for accurate fax tracking in CARLOS EMR.
 * </p>
 */
@Converter
public class FaxJobStatusConverter extends NullSafeEnumConverter<STATUS> {
    public FaxJobStatusConverter() {
        // Convert fax status enum for persistence to ensure accurate tracking of outgoing documents.
        super(STATUS.class, null);
    }
}
