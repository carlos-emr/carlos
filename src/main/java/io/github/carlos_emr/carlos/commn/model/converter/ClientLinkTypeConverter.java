package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the Type enum type.
 * Ensures safe conversion between database values and the corresponding enumeration,
 * particularly handling null or empty string edge cases gracefully.
 */
@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    // Initialize the null-safe converter with the specific Type class.
    public ClientLinkTypeConverter() {
        super(Type.class, null);
    }
}
