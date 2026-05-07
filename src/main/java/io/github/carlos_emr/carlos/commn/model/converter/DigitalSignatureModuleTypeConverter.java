package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Converter;
/**
 * JPA Attribute Converter for mapping DigitalSignatureModuleType enum values.
 * <p>
 * Translates between the Java enumeration and its underlying database column representation.
 *
 * @since 2026-05-05
 */

@Converter
public class DigitalSignatureModuleTypeConverter extends NullSafeEnumConverter<ModuleType> {
    public DigitalSignatureModuleTypeConverter() {
        super(ModuleType.class, null);
    }
}
