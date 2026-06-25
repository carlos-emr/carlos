package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the ModuleType enum type.
 * Ensures safe conversion between database values and the corresponding enumeration,
 * particularly handling null or empty string edge cases gracefully.
 */
@Converter
public class DigitalSignatureModuleTypeConverter extends NullSafeEnumConverter<ModuleType> {
    // Initialize the null-safe converter with the specific ModuleType class.
    public DigitalSignatureModuleTypeConverter() {
        super(ModuleType.class, null);
    }
}
