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

import java.util.*;

import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.prevention.dto.PreventionListItemDTO;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Service interface for managing Prevention and Immunization records.
 * Provides the business logic to track patient immunizations, preventative care measures,
 * and associated extended attributes (like lot numbers or clinical notes). Also handles
 * the configuration of which prevention types are active or hidden system-wide.
 */
public interface PreventionManager {

    /**
     * Retrieves all prevention records updated after a specific date.
     * Useful for synchronization and auditing purposes.
     * 
     * @param loggedInInfo Security context.
     * @param updatedAfterThisDateExclusive The threshold date.
     * @param itemsToReturn Maximum number of items to return.
     * @return List of updated Prevention entities.
     */
    public List<Prevention> getUpdatedAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateExclusive,
                                                int itemsToReturn);

    /**
     * Retrieves prevention records for a specific patient updated after a specific date.
     * 
     * @param loggedInInfo Security context.
     * @param demographicId The patient's demographic ID.
     * @param updatedAfterThisDateExclusive The threshold date.
     * @return List of updated Prevention entities for the patient.
     */
    public List<Prevention> getByDemographicIdUpdatedAfterDate(LoggedInInfo loggedInInfo, Integer demographicId,
                                                               Date updatedAfterThisDateExclusive);

    /**
     * Retrieves a specific prevention record by its unique identifier.
     * 
     * @param loggedInInfo Security context.
     * @param id The prevention record ID.
     * @return The requested Prevention entity.
     */
    public Prevention getPrevention(LoggedInInfo loggedInInfo, Integer id);

    /**
     * Retrieves the extended attributes (e.g., lot numbers, administration notes) linked to a prevention record.
     * 
     * @param loggedInInfo Security context.
     * @param preventionId The parent prevention record ID.
     * @return List of PreventionExt entities.
     */
    public List<PreventionExt> getPreventionExtByPrevention(LoggedInInfo loggedInInfo, Integer preventionId);

    /**
     * Retrieves the list of standard prevention/immunization types available in the system.
     * 
     * @return A list of prevention type names.
     */
    public ArrayList<String> getPreventionTypeList();

    /**
     * Retrieves a detailed list of prevention types mapped to their descriptions/categories.
     * 
     * @return A list of HashMaps containing the type mapping data.
     */
    public ArrayList<HashMap<String, String>> getPreventionTypeDescList();

    /**
     * Checks if a specific prevention item/type is globally hidden or disabled in the system.
     * 
     * @param item The prevention type name.
     * @return True if the item should be hidden from UI views.
     */
    public boolean hideItem(String item);

    /**
     * Registers new custom prevention items/types into the system configuration.
     * 
     * @param items A delimited string of custom prevention types.
     */
    public void addCustomPreventionItems(String items);

    /**
     * Creates or updates a prevention record along with its extended attributes in a single transaction.
     * 
     * @param prevention The primary Prevention entity.
     * @param exts A map of extended attribute key-value pairs (e.g., lot numbers).
     */
    public void addPreventionWithExts(Prevention prevention, HashMap<String, String> exts);

    /**
     * Retrieves prevention records matching a combination of program, provider, patient, and date criteria.
     * 
     * @param loggedInInfo Security context.
     * @param programId Optional program filter.
     * @param providerNo Optional provider filter.
     * @param demographicId Optional patient filter.
     * @param updatedAfterThisDateExclusive The threshold date.
     * @param itemsToReturn Maximum number of items to return.
     * @return List of matching Prevention entities.
     */
    public List<Prevention> getPreventionsByProgramProviderDemographicDate(LoggedInInfo loggedInInfo, Integer programId,
                                                                           String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive, int itemsToReturn);

    /**
     * Retrieves all prevention records for a specific patient demographic.
     * 
     * @param loggedInInfo Security context.
     * @param demographicNo The patient's demographic ID.
     * @return List of Prevention entities.
     */
    public List<Prevention> getPreventionsByDemographicNo(LoggedInInfo loggedInInfo, Integer demographicNo);

    /**
     * Retrieves clinical warnings or alerts associated with a patient's prevention history.
     * 
     * @param loggedInInfo Security context.
     * @param demo The patient's demographic ID as a String.
     * @return A formatted string of prevention warnings.
     */
    public String getWarnings(LoggedInInfo loggedInInfo, String demo);

    /**
     * Validates or sanitizes a prevention name against known system aliases.
     * 
     * @param k The raw prevention name.
     * @return The validated or mapped name.
     */
    public String checkNames(String k);

    /**
     * Checks if the entire prevention module is disabled system-wide.
     * 
     * @return True if disabled.
     */
    public boolean isDisabled();

    /**
     * Checks if the prevention configuration system has been fully initialized.
     * 
     * @return True if initialized.
     */
    public boolean isCreated();

    /**
     * Retrieves the set of prevention "stop signs" (critical alerts or contraindications).
     * 
     * @return Set of stop sign indicators.
     */
    public Set<String> getPreventionStopSigns();

    /**
     * Checks if a specific prevention type is explicitly disabled by system configuration.
     * 
     * @param name The prevention type name.
     * @return True if disabled.
     */
    public boolean isPrevDisabled(String name);

    /**
     * Retrieves the complete list of explicitly disabled prevention types.
     * 
     * @return List of disabled prevention names.
     */
    public List<String> getDisabledPreventions();

    /**
     * Checks if any items have been configured to be hidden in the system.
     * 
     * @return True if at least one item is hidden.
     */
    public boolean isHidePrevItemExist();

    /**
     * Updates the system configuration with a new list of disabled prevention types.
     * 
     * @param newDisabledPreventions The new list of disabled types.
     * @return True if the configuration was successfully updated.
     */
    public boolean setDisabledPreventions(List<String> newDisabledPreventions);

    /**
     * Retrieves only the records classified as immunizations (as opposed to general prevention) for a patient.
     * 
     * @param loggedInInfo Security context.
     * @param demographicNo The patient's demographic ID.
     * @return List of immunization Prevention entities.
     */
    public List<Prevention> getImmunizationsByDemographic(LoggedInInfo loggedInInfo, Integer demographicNo);

    /**
     * Retrieves the raw configuration string of custom prevention items.
     * 
     * @return The custom prevention configuration string.
     */
    public String getCustomPreventionItems();

    /**
     * Returns lightweight prevention DTOs for a demographic, bypassing the EAGER
     * PreventionExt collection. Enforces read privilege check.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context
     * @param demographicNo Integer the patient demographic number
     * @return List of PreventionListItemDTO for the patient's immunizations
     * @throws SecurityException if the caller lacks {@code _prevention} read privilege
     * @since 2026-04-11
     */
    List<PreventionListItemDTO> getPreventionDTOs(LoggedInInfo loggedInInfo, Integer demographicNo);
}
