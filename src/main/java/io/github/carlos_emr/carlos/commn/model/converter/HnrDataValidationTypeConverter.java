package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.HnrDataValidation.Type;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for HNR data validation types.
 * <p>
 * Converts validation type enumerations used in HNR (Health Network Routing)
 * to their corresponding persistent values in the CARLOS EMR database.
 * </p>
 */
@Converter
public class HnrDataValidationTypeConverter extends NullSafeEnumConverter<Type> {
    public HnrDataValidationTypeConverter() {
        // Convert HNR validation type enum to database value for consistent routing logic.
        super(Type.class, null);
    }
}
