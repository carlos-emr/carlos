package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxConfig.ProviderType;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for fax configuration provider types.
 * <p>
 * Converts the enum representing different fax service providers to the
 * appropriate database value for CARLOS EMR configuration storage.
 * </p>
 */
@Converter
public class FaxConfigProviderTypeConverter extends NullSafeEnumConverter<ProviderType> {
    public FaxConfigProviderTypeConverter() {
        // Map fax provider type enum to database column value for service routing.
        super(ProviderType.class, ProviderType.MIDDLEWARE);
    }
}
