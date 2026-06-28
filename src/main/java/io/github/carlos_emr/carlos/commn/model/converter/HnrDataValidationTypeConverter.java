package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;
/**
 * JPA Converter for translating HnrDataValidationType enums and attributes to and from database columns.
 *
 * @since 2026-06-26
 */

@Converter
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    public HnrDataValidationTypeConverter() {
        super(Type.class, null);
    }
}
