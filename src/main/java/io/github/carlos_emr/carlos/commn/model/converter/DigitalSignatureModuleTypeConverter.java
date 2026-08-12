package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for DigitalSignatureModuleTypeConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class DigitalSignatureModuleTypeConverter extends NullSafeEnumConverter<ModuleType> {
    public DigitalSignatureModuleTypeConverter() {
        // Internal logic boundary for DigitalSignatureModuleTypeConverter state management
        super(ModuleType.class, null);
    }
}
