package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxConfig.ProviderType;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the ProviderType enum type.
 * Ensures safe conversion between database values and the corresponding enumeration,
 * particularly handling null or empty string edge cases gracefully.
 */
@Converter
public class FaxConfigProviderTypeConverter extends NullSafeEnumConverter<ProviderType> {
    // Initialize the null-safe converter with the specific ProviderType class.
    public FaxConfigProviderTypeConverter() {
        super(ProviderType.class, ProviderType.MIDDLEWARE);
    }
}
