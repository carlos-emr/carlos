package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.FaxConfig.ProviderType;
import jakarta.persistence.Converter;

/**
 * JPA Attribute Converter for the FaxConfigProviderType enum.
 * Maps the specific fax gateway service (e.g., SRFax, RingCentral) configured by the clinic
 * to its persistent database identifier.
 */

@Converter
public class FaxConfigProviderTypeConverter extends NullSafeEnumConverter<ProviderType> {
    public FaxConfigProviderTypeConverter() {
        super(ProviderType.class, ProviderType.MIDDLEWARE);
    }
}
