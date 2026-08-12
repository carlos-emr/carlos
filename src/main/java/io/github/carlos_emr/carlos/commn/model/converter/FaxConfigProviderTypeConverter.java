package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxConfig.ProviderType;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for FaxConfigProviderTypeConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class FaxConfigProviderTypeConverter extends NullSafeEnumConverter<ProviderType> {
    public FaxConfigProviderTypeConverter() {
        // Internal logic boundary for FaxConfigProviderTypeConverter state management
        super(ProviderType.class, ProviderType.MIDDLEWARE);
    }
}
