package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the HnrDataValidationType enum to its database column.
 * Ensures that HnrDataValidationType values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    public HnrDataValidationTypeConverter() {
        // Initialize the converter mapping to the target enum class
        super(Type.class, null);
    }
}
