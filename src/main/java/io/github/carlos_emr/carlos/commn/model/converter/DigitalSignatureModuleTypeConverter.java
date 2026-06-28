package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Converter;
/**
 * JPA Converter for translating DigitalSignatureModuleType enums and attributes to and from database columns.
 *
 * @since 2026-06-26
 */

@Converter
public class DigitalSignatureModuleTypeConverter extends NullSafeEnumConverter<ModuleType> {
    public DigitalSignatureModuleTypeConverter() {
        super(ModuleType.class, null);
    }
}
