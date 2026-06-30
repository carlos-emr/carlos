package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for Health Number validation states.
 * Maps validation confidence levels to DB values.
 */
@Converter
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    public HnrDataValidationTypeConverter() {
        // Persist the provincial validation confidence score back to the patient record
        super(Type.class, null);
    }
}
