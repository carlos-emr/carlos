package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;

@Converter
/**
 * JPA attribute converter for the HnrDataValidationType enum.
 * Ensures the validation type is correctly serialized and deserialized
 * during database operations.
 */
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    public HnrDataValidationTypeConverter() {
        super(Type.class, null);
    }
}
