package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxConfig.ProviderType;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the selected fax transport provider.
 * Maps internal Enums to string identifiers used in configuration tables.
 */
@Converter
public class FaxConfigProviderTypeConverter extends NullSafeEnumConverter<ProviderType> {
    public FaxConfigProviderTypeConverter() {
        // Map configured provider selection to ensure the correct transport adapter is initialized
        super(ProviderType.class, ProviderType.MIDDLEWARE);
    }
}
