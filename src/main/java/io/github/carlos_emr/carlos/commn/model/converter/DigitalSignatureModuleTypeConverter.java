package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for digital signature module types.
 *
 * <p>Maps the Java enumeration to the corresponding database column value,
 * ensuring consistent persistence of signature configurations.</p>
 */

@Converter
public class DigitalSignatureModuleTypeConverter extends NullSafeEnumConverter<ModuleType> {
    // Maintain backward compatibility for legacy signature types during migration
    public DigitalSignatureModuleTypeConverter() {
        super(ModuleType.class, null);
    }
}
