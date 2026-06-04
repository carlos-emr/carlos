package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;

/**
 * JPA Attribute Converter for the ClientLinkType enum.
 * Handles the translation of client relationship typologies (e.g., Parent, Sibling, Power of Attorney)
 * between the object model and persistent storage.
 */

@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    public ClientLinkTypeConverter() {
        super(Type.class, null);
    }
}
