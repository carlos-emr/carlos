package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for client link types.
 * <p>
 * Maps the client link classification enum to the underlying string or integer
 * representation required by the CARLOS EMR database schema.
 * </p>
 */
@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    public ClientLinkTypeConverter() {
        // Map client link classification for database persistence to support legacy schema constraints.
        super(Type.class, null);
    }
}
