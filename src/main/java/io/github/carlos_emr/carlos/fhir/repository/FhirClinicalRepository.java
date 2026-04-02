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

import io.github.carlos_emr.carlos.fhir.client.MedplumFhirClient;
import io.github.carlos_emr.carlos.fhir.mapping.FhirResourceMapper;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link ClinicalRepository} implementation that stores and retrieves data
 * from a Medplum FHIR R4 server via the HAPI FHIR client.
 *
 * <p>This implementation uses a {@link FhirResourceMapper} to convert between
 * CARLOS domain entities and FHIR R4 resources. The mapper is entity-specific
 * (e.g., {@code DemographicToPatientMapper}) and must be provided at construction.</p>
 *
 * @param <T> the CARLOS domain entity type
 * @param <R> the FHIR R4 resource type (e.g., {@code Patient}, {@code AllergyIntolerance})
 * @param <ID> the entity identifier type
 * @since 2026-04-02
 */
public class FhirClinicalRepository<T, R extends Resource, ID> implements ClinicalRepository<T, ID> {

    private final MedplumFhirClient fhirClient;
    private final FhirResourceMapper<T, R> mapper;
    private final Class<R> resourceType;

    /**
     * Creates a FHIR-backed clinical repository.
     *
     * @param fhirClient   the Medplum FHIR client for server communication
     * @param mapper       bidirectional mapper between domain entity and FHIR resource
     * @param resourceType the FHIR resource class (e.g., {@code Patient.class})
     */
    public FhirClinicalRepository(MedplumFhirClient fhirClient,
                                  FhirResourceMapper<T, R> mapper,
                                  Class<R> resourceType) {
        this.fhirClient = fhirClient;
        this.mapper = mapper;
        this.resourceType = resourceType;
    }

    @Override
    public Optional<T> findById(ID id) {
        R resource = fhirClient.read(resourceType, String.valueOf(id));
        if (resource == null) {
            return Optional.empty();
        }
        return Optional.of(mapper.fromFhir(resource));
    }

    @Override
    public List<T> findAll(int offset, int limit) {
        Bundle bundle = fhirClient.search(resourceType, offset, limit);
        List<T> results = new ArrayList<>();
        if (bundle.hasEntry()) {
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (resourceType.isInstance(entry.getResource())) {
                    results.add(mapper.fromFhir(resourceType.cast(entry.getResource())));
                }
            }
        }
        return results;
    }

    @Override
    public T save(T entity) {
        R resource = mapper.toFhir(entity);
        R saved = fhirClient.createOrUpdate(resource);
        return mapper.fromFhir(saved);
    }

    @Override
    public boolean delete(ID id) {
        return fhirClient.delete(resourceType, String.valueOf(id));
    }

    @Override
    public long count() {
        Bundle bundle = fhirClient.search(resourceType, 0, 0);
        return bundle.getTotal();
    }
}
