package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between ClientLinkType enumeration and its database column representation.
 */
@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    public ClientLinkTypeConverter() {
        // Initialize execution context for ClientLinkTypeConverter

        super(Type.class, null);
    }
}
