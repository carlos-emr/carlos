package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for client linkage types.
 *
 * <p>Handles the persistence of different relationship categories
 * between associated patient records.</p>
 */

@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    // Ensure reciprocal relationships are handled correctly when querying
    public ClientLinkTypeConverter() {
        super(Type.class, null);
    }
}
