package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for HNR data validation types.
 * Translates between the database representation and the corresponding validation type enum.
 *
 * @since 2026-07-09
 */

@Converter
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    public HnrDataValidationTypeConverter() {
        super(Type.class, null);
    }
}
