package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxConfig.ProviderType;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for fax provider types.
 *
 * <p>Translates the configured fax transport mechanism (e.g., MIDDLEWARE, SRFAX)
 * for persistence.</p>
 */

@Converter
public class FaxConfigProviderTypeConverter extends NullSafeEnumConverter<ProviderType> {
    // Ensure the default provider is selected if the database value is null
    public FaxConfigProviderTypeConverter() {
        super(ProviderType.class, ProviderType.MIDDLEWARE);
    }
}
