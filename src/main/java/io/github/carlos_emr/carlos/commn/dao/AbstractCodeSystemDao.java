/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.AbstractCodeSystemModel;

/**
 * DAO interface for medical code system entities (ICD-9, ICD-10, SNOMED CT, etc.).
 * <p>
 * Extends {@link AbstractDao} with operations specific to searching and looking up
 * diagnostic and clinical codes used throughout the CARLOS EMR billing and
 * clinical documentation modules.
 *
 * @param <T> the code system entity type, which must extend {@link AbstractCodeSystemModel}
 * @since 2001
 */
public interface AbstractCodeSystemDao<T extends AbstractCodeSystemModel<?>> extends AbstractDao<T> {

    /**
     * Searches for codes matching the given search term.
     *
     * @param term String the search term to match against code descriptions or values
     * @return List of matching code system entities
     */
    public List<T> searchCode(String term);

    /**
     * Finds a single code system entity by its exact code value.
     *
     * @param code String the code to look up (e.g., "250" for ICD-9 Diabetes)
     * @return the matching entity, or {@code null} if not found
     */
    public T findByCode(String code);

    /**
     * Finds a code system entity by the name of its coding system.
     *
     * @param codingSystem String the name of the coding system to search
     * @return the matching code system model, or {@code null} if not found
     */
    public AbstractCodeSystemModel<?> findByCodingSystem(String codingSystem);

    /** Enumeration of supported medical coding systems. */
    public static enum codingSystem {icd9, icd10, ichppccode, msp, SnomedCore}

    /**
     * Returns the DAO class corresponding to the specified coding system.
     *
     * @param codeSystem the coding system enumeration value
     * @return Class the DAO class for the specified coding system
     * @throws IllegalArgumentException if the coding system is not supported
     */
    public static Class<?> getDaoName(codingSystem codeSystem) {
        Class<?> object;
        switch (codeSystem) {
            case SnomedCore:
                object = SnomedCoreDao.class;
                break;
            case icd10:
                object = Icd10Dao.class;
                break;
            case icd9:
                object = Icd9Dao.class;
                break;
            case ichppccode:
                object = IchppccodeDao.class;
                break;
            case msp:
                object = DiagnosticCodeDao.class;
                break;
            default:
                throw new IllegalArgumentException("Unsupported code system: " + codeSystem + ". Please use one of icd9, ichppccode, snomedcore");
        }
        return object;
    }
}
