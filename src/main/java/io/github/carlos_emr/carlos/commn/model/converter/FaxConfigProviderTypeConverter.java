package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxConfig.ProviderType;
import jakarta.persistence.Converter;

@Converter
public class FaxConfigProviderTypeConverter extends NullSafeEnumConverter<ProviderType> {
    public FaxConfigProviderTypeConverter() {
        super(ProviderType.class, ProviderType.MIDDLEWARE);
    }
}
