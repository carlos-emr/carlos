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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * {@link ClinicalRepository} implementation that writes to both MySQL (via JPA)
 * and Medplum (via FHIR), while reading from a configurable primary source.
 *
 * <p>This is the transitional implementation used during migration phases 1-2.
 * Writes always go to both stores. Reads come from the primary source (MySQL
 * by default, switchable to FHIR via {@link #setReadFromFhir(boolean)}).</p>
 *
 * <p>FHIR write failures are logged but do not fail the operation — MySQL
 * remains the source of truth until reads are switched over.</p>
 *
 * @param <T>  the domain entity type
 * @param <ID> the entity identifier type
 * @since 2026-04-02
 */
public class DualWriteClinicalRepository<T, ID> implements ClinicalRepository<T, ID> {

    private static final Logger logger = LoggerFactory.getLogger(DualWriteClinicalRepository.class);

    private final ClinicalRepository<T, ID> jpaRepository;
    private final ClinicalRepository<T, ID> fhirRepository;
    private volatile boolean readFromFhir;

    /**
     * Creates a dual-write repository with MySQL as the default read source.
     *
     * @param jpaRepository  the MySQL/JPA-backed repository
     * @param fhirRepository the Medplum/FHIR-backed repository
     */
    public DualWriteClinicalRepository(ClinicalRepository<T, ID> jpaRepository,
                                       ClinicalRepository<T, ID> fhirRepository) {
        this.jpaRepository = jpaRepository;
        this.fhirRepository = fhirRepository;
        this.readFromFhir = false;
    }

    /**
     * Switches the read source between MySQL and FHIR.
     *
     * @param readFromFhir {@code true} to read from Medplum FHIR, {@code false} for MySQL
     */
    public void setReadFromFhir(boolean readFromFhir) {
        this.readFromFhir = readFromFhir;
    }

    @Override
    public Optional<T> findById(ID id) {
        return readSource().findById(id);
    }

    @Override
    public List<T> findAll(int offset, int limit) {
        return readSource().findAll(offset, limit);
    }

    @Override
    public T save(T entity) {
        T saved = jpaRepository.save(entity);
        try {
            fhirRepository.save(entity);
        } catch (Exception e) {
            logger.warn("FHIR dual-write failed for {}, MySQL write succeeded. "
                    + "Entity will be reconciled on next sync.", entity.getClass().getSimpleName(), e);
        }
        return saved;
    }

    @Override
    public boolean delete(ID id) {
        boolean deleted = jpaRepository.delete(id);
        try {
            fhirRepository.delete(id);
        } catch (Exception e) {
            logger.warn("FHIR dual-delete failed for id={}, MySQL delete succeeded.", id, e);
        }
        return deleted;
    }

    @Override
    public long count() {
        return readSource().count();
    }

    private ClinicalRepository<T, ID> readSource() {
        return readFromFhir ? fhirRepository : jpaRepository;
    }
}
