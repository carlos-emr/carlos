package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for the digital signature module type.
 * <p>
 * Maps the internal enum representation of signature module types to their
 * corresponding database column values in CARLOS EMR.
 * </p>
 */
@Converter
public class DigitalSignatureModuleTypeConverter extends NullSafeEnumConverter<ModuleType> {
    public DigitalSignatureModuleTypeConverter() {
        // Map digital signature module enum to database value to maintain legacy schema compatibility.
        super(ModuleType.class, null);
    }
}
