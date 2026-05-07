package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import jakarta.persistence.Converter;
/**
 * JPA Attribute Converter for Fax Job Status Converter.
 *
 * @since 2026-05-05
 */


@Converter
public class FaxJobStatusConverter extends NullSafeEnumConverter<STATUS> {
    public FaxJobStatusConverter() {
        super(STATUS.class, null);
    }
}
