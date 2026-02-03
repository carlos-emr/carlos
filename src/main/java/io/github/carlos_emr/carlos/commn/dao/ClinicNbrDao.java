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
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;

import io.github.carlos_emr.carlos.commn.model.ClinicNbr;

public interface ClinicNbrDao extends AbstractDao<ClinicNbr> {

    ArrayList<ClinicNbr> findAll();

    Integer removeEntry(Integer id);

    int addEntry(String nbrValue, String nbrString);
}
