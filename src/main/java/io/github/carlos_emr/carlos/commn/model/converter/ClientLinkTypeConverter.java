package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for client relationship types.
 * Translates relationship codes into programmatic enum representations.
 */
@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    public ClientLinkTypeConverter() {
        // Translate categorical relationship definitions to standardized persistence characters
        super(Type.class, null);
    }
}
