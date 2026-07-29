package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;
/**
 * JPA converter for mapping ClientLinkType enumerations to integer or string formats in the database.
 */

@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    public ClientLinkTypeConverter() {
        super(Type.class, null);
    }
}
