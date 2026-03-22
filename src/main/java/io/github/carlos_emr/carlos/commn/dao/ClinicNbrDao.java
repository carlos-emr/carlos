/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;

import io.github.carlos_emr.carlos.commn.model.ClinicNbr;

/**
 * DAO interface for clinic operations.
 *
 * @since 2001
 */

public interface ClinicNbrDao extends AbstractDao<ClinicNbr> {

    /**
     * Find All.
     * @return ArrayList<ClinicNbr>
     */
    ArrayList<ClinicNbr> findAll();

    /**
     * Remove Entry.
     *
     * @param id Integer the id
     * @return Integer
     */
    Integer removeEntry(Integer id);

    /**
     * Add Entry.
     *
     * @param nbrValue String the nbrValue
     * @param nbrString String the nbrString
     * @return int
     */
    int addEntry(String nbrValue, String nbrString);
}
