package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxConfig.ProviderType;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for FaxConfigProviderTypeConverter, mapping entity attributes to database columns.
 */
@Converter
public class FaxConfigProviderTypeConverter extends NullSafeEnumConverter<ProviderType> {
    // Handles the conversion logic for FaxConfigProviderTypeConverter to maintain data persistence

    public FaxConfigProviderTypeConverter() {
        super(ProviderType.class, ProviderType.MIDDLEWARE);
    }
}
