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
package io.github.carlos_emr.carlos.managers;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.DemographicSets;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Service interface for managing named demographic set groupings in the
 * CARLOS EMR system.
 *
 * <p>Demographic sets allow healthcare providers to define and maintain
 * named groups of patients for reporting, outreach, or administrative purposes.</p>
 *
 * @see DemographicSetsManagerImpl
 * @see io.github.carlos_emr.carlos.commn.model.DemographicSets
 * @since 2026-03-17
 */
public interface DemographicSetsManager {

    /**
     * Retrieves all demographic set records with pagination.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param offset int the starting index for pagination
     * @param itemsToReturn int the maximum number of records to return
     * @return List of DemographicSets records
     */
    public List<DemographicSets> getAllDemographicSets(LoggedInInfo loggedInInfo, int offset, int itemsToReturn);

    /**
     * Retrieves all distinct demographic set names.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @return List of String set names
     */
    public List<String> getNames(LoggedInInfo loggedInInfo);

    /**
     * Retrieves all demographic set entries for a given set name.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param setName String the name of the demographic set
     * @return List of DemographicSets entries belonging to the named set
     */
    public List<DemographicSets> getByName(LoggedInInfo loggedInInfo, String setName);
}
