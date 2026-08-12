package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for HnrDataValidationTypeConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    public HnrDataValidationTypeConverter() {
        // Internal logic boundary for HnrDataValidationTypeConverter state management
        super(Type.class, null);
    }
}
