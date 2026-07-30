package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;

/**
 * Converter class for HnrDataValidationType to handle database transformations.
 *
 * This class is part of the CARLOS EMR system.
 */


@Converter
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    public HnrDataValidationTypeConverter() {
        super(Type.class, null);
    }
}
