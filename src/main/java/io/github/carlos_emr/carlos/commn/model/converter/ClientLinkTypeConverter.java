package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;

/**
 * Handles JPA attribute conversion for ClientLinkType types.
 *
 * @since 2026-06-10
 */
@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    public ClientLinkTypeConverter() {
        super(Type.class, null);
    }
}
