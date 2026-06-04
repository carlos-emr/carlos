package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Converter;

/**
 * JPA Attribute Converter for mapping the DigitalSignatureModuleType enum to its database column representation.
 * Ensures type safety in Java code while maintaining compatibility with the underlying legacy schema codes.
 */

@Converter
public class DigitalSignatureModuleTypeConverter extends NullSafeEnumConverter<ModuleType> {
    public DigitalSignatureModuleTypeConverter() {
        super(ModuleType.class, null);
    }
}
