package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Converter;

/**
 * DigitalSignatureModuleTypeConverter provides functionality and data models for the DigitalSignatureModuleTypeConverter domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
 */
@Converter
public class DigitalSignatureModuleTypeConverter extends NullSafeEnumConverter<ModuleType> {
    public DigitalSignatureModuleTypeConverter() {
        super(ModuleType.class, null);
    }
}
