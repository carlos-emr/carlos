package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for client link types.
 * Translates between the database representation and the ClientLinkType enum.
 *
 * @since 2026-07-09
 */

@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    public ClientLinkTypeConverter() {
        super(Type.class, null);
    }
}
