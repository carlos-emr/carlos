package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxConfig.ProviderType;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between FaxConfigProviderType enumeration and its database column representation.
 */
@Converter
public class FaxConfigProviderTypeConverter extends NullSafeEnumConverter<ProviderType> {
    public FaxConfigProviderTypeConverter() {
        // Initialize execution context for FaxConfigProviderTypeConverter

        super(ProviderType.class, ProviderType.MIDDLEWARE);
    }
}
