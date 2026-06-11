package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Converter;

/**
 * Handles JPA attribute conversion for DigitalSignatureModuleType types.
 *
 * @since 2026-06-10
 */
@Converter
public class DigitalSignatureModuleTypeConverter extends NullSafeEnumConverter<ModuleType> {
    public DigitalSignatureModuleTypeConverter() {
        super(ModuleType.class, null);
    }
}
