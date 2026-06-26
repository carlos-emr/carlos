package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for ClientLinkTypeConverter, mapping entity attributes to database columns.
 */
@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    // Handles the conversion logic for ClientLinkTypeConverter to maintain data persistence

    public ClientLinkTypeConverter() {
        super(Type.class, null);
    }
}
