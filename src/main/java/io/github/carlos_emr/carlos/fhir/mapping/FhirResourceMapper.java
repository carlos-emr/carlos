/**
 * Copyright (c) 2026. CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.fhir.mapping;

import org.hl7.fhir.r4.model.Resource;

/**
 * Bidirectional mapper between a CARLOS domain entity and a FHIR R4 resource.
 *
 * <p>Implementations handle the field-level translation between the legacy
 * MySQL-backed model and its FHIR equivalent. Each clinical entity that
 * participates in the FHIR migration requires one mapper implementation
 * (e.g., {@code DemographicToPatientMapper}, {@code AllergyToAllergyIntoleranceMapper}).</p>
 *
 * <p>Mappers must be stateless and thread-safe.</p>
 *
 * @param <T> the CARLOS domain entity type (e.g., {@code Demographic})
 * @param <R> the FHIR R4 resource type (e.g., {@code Patient})
 * @since 2026-04-02
 */
public interface FhirResourceMapper<T, R extends Resource> {

    /**
     * Converts a CARLOS domain entity to a FHIR R4 resource.
     *
     * @param entity the domain entity to convert
     * @return the equivalent FHIR resource, never null
     */
    R toFhir(T entity);

    /**
     * Converts a FHIR R4 resource to a CARLOS domain entity.
     *
     * @param resource the FHIR resource to convert
     * @return the equivalent domain entity, never null
     */
    T fromFhir(R resource);
}
