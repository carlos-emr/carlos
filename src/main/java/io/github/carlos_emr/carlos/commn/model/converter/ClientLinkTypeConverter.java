package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the ClientLinkType enum to its database column.
 * Ensures that ClientLinkType values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    public ClientLinkTypeConverter() {
        // Initialize the converter mapping to the target enum class
        super(Type.class, null);
    }
}
