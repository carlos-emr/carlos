package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the digital signature module type.
 * Maps module enums to database values to maintain referential integrity.
 */
@Converter
public class DigitalSignatureModuleTypeConverter extends NullSafeEnumConverter<ModuleType> {
    public DigitalSignatureModuleTypeConverter() {
        // Convert programmatic enum states into persistence integers for standard JPA mappings
        super(ModuleType.class, null);
    }
}
