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

import io.github.carlos_emr.carlos.commn.model.DrugDispensingMapping;

/**
 * DAO interface for drug and prescription operations.
 *
 * @since 2001
 */

public interface DrugDispensingMappingDao extends AbstractDao<DrugDispensingMapping> {
    /**
     * Find Mapping By Din.
     *
     * @param din String the din
     * @return DrugDispensingMapping
     */
    DrugDispensingMapping findMappingByDin(String din);

    /**
     * Find Mapping.
     *
     * @param din String the din
     * @param duration String the duration
     * @param durUnit String the durUnit
     * @param freqCode String the freqCode
     * @param quantity String the quantity
     * @param takeMin Float the takeMin
     * @param takeMax Float the takeMax
     * @return DrugDispensingMapping
     */
    DrugDispensingMapping findMapping(String din, String duration, String durUnit, String freqCode, String quantity, Float takeMin, Float takeMax);
}
