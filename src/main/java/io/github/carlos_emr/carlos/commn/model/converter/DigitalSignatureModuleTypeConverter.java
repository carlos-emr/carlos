package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for DigitalSignatureModuleTypeConverter, mapping entity attributes to database columns.
 */
@Converter
public class DigitalSignatureModuleTypeConverter extends NullSafeEnumConverter<ModuleType> {
    // Handles the conversion logic for DigitalSignatureModuleTypeConverter to maintain data persistence

    public DigitalSignatureModuleTypeConverter() {
        super(ModuleType.class, null);
    }
}
