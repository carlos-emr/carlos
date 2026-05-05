package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import jakarta.persistence.Converter;

/**
 * FaxJobStatusConverter provides functionality and data models for the FaxJobStatusConverter domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
 */
@Converter
public class FaxJobStatusConverter extends NullSafeEnumConverter<STATUS> {
    public FaxJobStatusConverter() {
        super(STATUS.class, null);
    }
}
