package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;

/**
 * ClientLinkTypeConverter provides functionality and data models for the ClientLinkTypeConverter domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
 */
@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    public ClientLinkTypeConverter() {
        super(Type.class, null);
    }
}
