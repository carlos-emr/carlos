/**
 * Copyright (c) 2026. CARLOS EMR Project Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * CARLOS EMR Project: https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Collections;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link Hl7TextInfoChainLockDao} over JPA: {@code PESSIMISTIC_WRITE} locking reads (SELECT ... FOR
 * UPDATE), every one {@code MANDATORY}.
 */
@Repository
public class Hl7TextInfoChainLockDaoImpl implements Hl7TextInfoChainLockDao {

    @PersistenceContext
    protected EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Hl7TextInfo> lockByAccession(String accession) {
        if (accession == null || accession.isBlank()) {
            return Collections.emptyList();
        }
        return entityManager.createQuery(
                        "SELECT h FROM Hl7TextInfo h WHERE h.accessionNumber = :value ORDER BY h.labNumber, h.id",
                        Hl7TextInfo.class)
                .setParameter("value", accession)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Hl7TextInfo> lockByFillerOrderNumber(String fillerOrderNumber) {
        if (fillerOrderNumber == null || fillerOrderNumber.isBlank()) {
            return Collections.emptyList();
        }
        return entityManager.createQuery(
                        "SELECT h FROM Hl7TextInfo h WHERE h.fillerOrderNum = :value ORDER BY h.labNumber, h.id",
                        Hl7TextInfo.class)
                .setParameter("value", fillerOrderNumber)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Hl7TextInfo lockByLabNumber(int labNumber) {
        List<Hl7TextInfo> rows = entityManager.createQuery(
                        "SELECT h FROM Hl7TextInfo h WHERE h.labNumber = :value ORDER BY h.id", Hl7TextInfo.class)
                .setParameter("value", labNumber)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }
}
