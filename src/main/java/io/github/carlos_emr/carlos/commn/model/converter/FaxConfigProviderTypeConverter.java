package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxConfig.ProviderType;
import jakarta.persistence.Converter;
/**
 * JPA converter for mapping FaxConfigProviderType enumerations to database column values.
 */

@Converter
public class FaxConfigProviderTypeConverter extends NullSafeEnumConverter<ProviderType> {
    public FaxConfigProviderTypeConverter() {
        super(ProviderType.class, ProviderType.MIDDLEWARE);
    }
}
