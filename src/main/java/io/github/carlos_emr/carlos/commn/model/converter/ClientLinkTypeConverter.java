package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.ClientLink.Type;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for ClientLinkTypeConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class ClientLinkTypeConverter extends NullSafeEnumConverter<Type> {
    public ClientLinkTypeConverter() {
        // Internal logic boundary for ClientLinkTypeConverter state management
        super(Type.class, null);
    }
}
