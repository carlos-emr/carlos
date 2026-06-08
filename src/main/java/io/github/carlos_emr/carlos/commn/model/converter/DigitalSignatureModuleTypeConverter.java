package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the DigitalSignatureModuleType enum to its database column.
 * Ensures that DigitalSignatureModuleType values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class DigitalSignatureModuleTypeConverter extends NullSafeEnumConverter<ModuleType> {
    public DigitalSignatureModuleTypeConverter() {
        // Initialize the converter mapping to the target enum class
        super(ModuleType.class, null);
    }
}
