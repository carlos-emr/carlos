package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for HNR data validation rules.
 *
 * <p>Maps the validation severity or type enum to its database value.</p>
 */

@Converter
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    // Map unknown validation types to a safe default to prevent runtime errors
    public HnrDataValidationTypeConverter() {
        super(Type.class, null);
    }
}
