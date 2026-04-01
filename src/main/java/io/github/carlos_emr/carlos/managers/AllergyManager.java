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

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

public interface AllergyManager {

    public Allergy getAllergy(LoggedInInfo loggedInInfo, Integer id);

    public List<Allergy> getActiveAllergies(LoggedInInfo loggedInInfo, Integer demographicNo);

    /**
     * Persists a new allergy record and writes an audit log entry.
     * Sets {@code providerNo} from {@code loggedInInfo} when not already populated.
     *
     * @param loggedInInfo the authenticated provider performing the save
     * @param allergy      the {@link Allergy} to persist
     * @return the saved {@link Allergy} with its generated identifier
     */
    public Allergy saveAllergy(LoggedInInfo loggedInInfo, Allergy allergy);

    /**
     * Merges changes to an existing allergy record and writes an audit log entry.
     * Sets {@code providerNo} from {@code loggedInInfo} when not already populated.
     *
     * @param loggedInInfo the authenticated provider performing the update
     * @param allergy      the {@link Allergy} with updated values
     * @return the managed {@link Allergy} returned by the merge
     */
    public Allergy updateAllergy(LoggedInInfo loggedInInfo, Allergy allergy);

    public List<Allergy> getUpdatedAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateInclusive,
                                             int itemsToReturn);

    public List<Allergy> getByDemographicIdUpdatedAfterDate(LoggedInInfo loggedInInfo, Integer demographicId,
                                                            Date updatedAfterThisDate);

    public List<Allergy> getAllergiesByProgramProviderDemographicDate(LoggedInInfo loggedInInfo, Integer programId,
                                                                      String providerNo, Integer demographicId, Calendar updatedAfterThisDateInclusive, int itemsToReturn);
}
