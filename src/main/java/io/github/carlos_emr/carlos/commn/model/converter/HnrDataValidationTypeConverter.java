package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for HNR data validation types.
 * Handles the serialization and deserialization of validation status enums
 * when persisting Health Network Record data.
 */
@Converter
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    public HnrDataValidationTypeConverter() {
        super(Type.class, null);
    }
}
