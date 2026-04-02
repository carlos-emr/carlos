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
package io.github.carlos_emr.carlos.fhir.repository;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction layer for clinical data access that decouples managers from
 * the underlying storage implementation (MySQL DAO or Medplum FHIR server).
 *
 * <p>This interface is the key enabler for the phased MySQL → Medplum FHIR
 * migration. Each clinical entity gets three implementations:</p>
 * <ul>
 *   <li>{@code JpaClinicalRepository} — delegates to existing Hibernate DAOs (current behavior)</li>
 *   <li>{@code FhirClinicalRepository} — delegates to Medplum via HAPI FHIR R4 client</li>
 *   <li>{@code DualWriteClinicalRepository} — writes to both stores, reads from a configurable source</li>
 * </ul>
 *
 * <p>Managers are wired to this interface. Swapping the active implementation
 * (via Spring profiles or feature flags) switches the backing store without
 * touching manager code.</p>
 *
 * @param <T>  the domain entity type (e.g., {@code Demographic}, {@code Allergy})
 * @param <ID> the entity identifier type (e.g., {@code Integer}, {@code Long})
 * @since 2026-04-02
 */
public interface ClinicalRepository<T, ID> {

    /**
     * Finds an entity by its identifier.
     *
     * @param id the entity identifier
     * @return an {@code Optional} containing the entity, or empty if not found
     */
    Optional<T> findById(ID id);

    /**
     * Returns a paginated list of entities.
     *
     * @param offset zero-based offset for pagination
     * @param limit  maximum number of entities to return
     * @return list of entities, possibly empty
     */
    List<T> findAll(int offset, int limit);

    /**
     * Saves (creates or updates) an entity.
     *
     * @param entity the entity to save
     * @return the saved entity (may have generated ID populated)
     */
    T save(T entity);

    /**
     * Deletes an entity by its identifier.
     *
     * @param id the entity identifier
     * @return {@code true} if the entity was found and deleted
     */
    boolean delete(ID id);

    /**
     * Returns the total count of entities.
     *
     * @return entity count
     */
    long count();
}
