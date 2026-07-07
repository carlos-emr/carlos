package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between HnrDataValidationType enumeration and its database column representation.
 */
@Converter
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    public HnrDataValidationTypeConverter() {
        // Initialize execution context for HnrDataValidationTypeConverter

        super(Type.class, null);
    }
}
