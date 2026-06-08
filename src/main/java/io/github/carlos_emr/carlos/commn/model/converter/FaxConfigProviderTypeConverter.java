package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxConfig.ProviderType;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the FaxConfigProviderType enum to its database column.
 * Ensures that FaxConfigProviderType values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class FaxConfigProviderTypeConverter extends NullSafeEnumConverter<ProviderType> {
    public FaxConfigProviderTypeConverter() {
        // Initialize the converter mapping to the target enum class
        super(ProviderType.class, ProviderType.MIDDLEWARE);
    }
}
