package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;

/**
 * HnrDataValidationTypeConverter provides functionality and data models for the HnrDataValidationTypeConverter domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
 */
@Converter
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    public HnrDataValidationTypeConverter() {
        super(Type.class, null);
    }
}
