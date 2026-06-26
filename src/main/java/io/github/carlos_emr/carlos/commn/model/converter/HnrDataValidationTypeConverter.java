package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for HnrDataValidationTypeConverter, mapping entity attributes to database columns.
 */
@Converter
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    // Handles the conversion logic for HnrDataValidationTypeConverter to maintain data persistence

    public HnrDataValidationTypeConverter() {
        super(Type.class, null);
    }
}
