package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;
/**
 * JPA converter for translating between ClientLinkType enums and their database column representations.
 */

@Converter

public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    public ClientLinkTypeConverter() {
        super(Type.class, null);
    }
}
