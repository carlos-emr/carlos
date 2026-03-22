/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.PMmodule.service;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientRestriction;
import io.github.carlos_emr.carlos.PMmodule.exception.ClientAlreadyRestrictedException;

/**
 * Service interface for managing client service restrictions within the CARLOS EMR
 * Program Management module.
 *
 * <p>Service restrictions prevent clients from being admitted to specific programs for
 * a defined time period. This interface provides operations for querying, creating,
 * terminating, and toggling the enabled/disabled state of restrictions.</p>
 *
 * @see ClientRestrictionManagerImpl
 * @see ProgramClientRestriction
 * @since 2005
 */
public interface ClientRestrictionManager {

    /**
     * Retrieves active (enabled) service restrictions for a specific program as of a given date.
     *
     * @param programId int the program identifier
     * @param asOfDate Date the reference date for determining active restrictions
     * @return List&lt;ProgramClientRestriction&gt; list of active restrictions for the program
     */
    List<ProgramClientRestriction> getActiveRestrictionsForProgram(int programId, Date asOfDate);

    /**
     * Retrieves disabled service restrictions for a specific program as of a given date.
     *
     * @param programId Integer the program identifier
     * @param asOfDate Date the reference date for determining restrictions
     * @return List&lt;ProgramClientRestriction&gt; list of disabled restrictions for the program
     */
    List<ProgramClientRestriction> getDisabledRestrictionsForProgram(Integer programId, Date asOfDate);

    /**
     * Retrieves active service restrictions for a specific client as of a given date.
     *
     * @param demographicNo int the client demographic number
     * @param asOfDate Date the reference date for determining active restrictions
     * @return List&lt;ProgramClientRestriction&gt; list of active restrictions for the client
     */
    List<ProgramClientRestriction> getActiveRestrictionsForClient(int demographicNo, Date asOfDate);

    /**
     * Retrieves active service restrictions for a client within a specific facility.
     *
     * @param demographicNo int the client demographic number
     * @param facilityId int the facility identifier
     * @param asOfDate Date the reference date for determining active restrictions
     * @return List&lt;ProgramClientRestriction&gt; list of active restrictions for the client at the facility
     */
    List<ProgramClientRestriction> getActiveRestrictionsForClient(int demographicNo, int facilityId, Date asOfDate);

    /**
     * Retrieves disabled service restrictions for a specific client as of a given date.
     *
     * @param demographicNo int the client demographic number
     * @param asOfDate Date the reference date for determining restrictions
     * @return List&lt;ProgramClientRestriction&gt; list of disabled restrictions for the client
     */
    List<ProgramClientRestriction> getDisabledRestrictionsForClient(int demographicNo, Date asOfDate);

    /**
     * Checks whether a service restriction is currently in effect for a client in a specific program.
     *
     * @param programId int the program identifier
     * @param demographicNo int the client demographic number
     * @param asOfDate Date the reference date for the check
     * @return ProgramClientRestriction the active restriction if one exists, or {@code null} if none
     */
    ProgramClientRestriction checkClientRestriction(int programId, int demographicNo, Date asOfDate);

    /**
     * Saves a new or existing client service restriction.
     * Prevents duplicate active restrictions for the same client and program.
     *
     * @param restriction ProgramClientRestriction the restriction to save
     * @throws ClientAlreadyRestrictedException if the client already has an active restriction in the program
     */
    void saveClientRestriction(ProgramClientRestriction restriction) throws ClientAlreadyRestrictedException;

    /**
     * Terminates a service restriction early by setting its end date to the current date.
     *
     * @param programClientRestrictionId int the restriction identifier
     * @param providerNo String the provider number authorizing early termination
     */
    void terminateEarly(int programClientRestrictionId, String providerNo);

    /**
     * Disables a service restriction without removing it from the database.
     *
     * @param restrictionId int the restriction identifier
     */
    void disableClientRestriction(int restrictionId);

    /**
     * Re-enables a previously disabled service restriction.
     *
     * @param restrictionId Integer the restriction identifier
     */
    void enableClientRestriction(Integer restrictionId);
}
