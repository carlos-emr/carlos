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

package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.ClientReferral;

/**
 * Data access interface for managing {@link ClientReferral} entities within the
 * Program Management module.
 *
 * <p>Provides CRUD operations and query methods for client referrals,
 * including filtering by client, facility, program, and referral status.</p>
 *
 * @since 2005-01-18
 * @see ClientReferral
 * @see ClientReferralDAOImpl
 */
public interface ClientReferralDAO {

    /**
     * Retrieves all client referrals in the system.
     *
     * @return List&lt;ClientReferral&gt; all referral records
     */
    public List<ClientReferral> getReferrals();

    /**
     * Retrieves all referrals for a specific client, enriched with referral history display data.
     *
     * @param clientId Long the demographic ID of the client
     * @return List&lt;ClientReferral&gt; referrals for the specified client
     * @throws IllegalArgumentException if clientId is {@code null} or not positive
     */
    public List<ClientReferral> getReferrals(Long clientId);

    /**
     * Retrieves referrals for a client filtered by facility, enriched with referral history display data.
     *
     * @param clientId Long the demographic ID of the client
     * @param facilityId Integer the facility ID to filter by
     * @return List&lt;ClientReferral&gt; referrals matching the client and facility criteria
     * @throws IllegalArgumentException if clientId or facilityId is invalid
     */
    public List<ClientReferral> getReferralsByFacility(Long clientId, Integer facilityId);

    /**
     * Enriches a list of referral results with the referring program name and
     * external program indicator for referral history reporting.
     *
     * @param lResult List&lt;ClientReferral&gt; the referral records to enrich
     * @return List&lt;ClientReferral&gt; the enriched referral records
     */
    // [ 1842692 ] RFQ Feature - temp change for pmm referral history report
    // - suggestion: to add a new field to the table client_referral (Referring program/agency)
    public List<ClientReferral> displayResult(List<ClientReferral> lResult);

    /**
     * Retrieves active, pending, or unknown-status referrals for a client,
     * optionally filtered by facility.
     *
     * @param clientId Long the demographic ID of the client
     * @param facilityId Integer the facility ID to filter by, or {@code null} for all facilities
     * @return List&lt;ClientReferral&gt; active referrals for the specified client
     * @throws IllegalArgumentException if clientId is {@code null} or not positive
     */
    public List<ClientReferral> getActiveReferrals(Long clientId, Integer facilityId);

    /**
     * Retrieves active or current referrals for a client in a specific program,
     * ordered by referral date descending.
     *
     * @param clientId Long the demographic ID of the client
     * @param programId Long the program ID to filter by
     * @return List&lt;ClientReferral&gt; active referrals matching client and program
     * @throws IllegalArgumentException if clientId or programId is invalid
     */
    public List<ClientReferral> getActiveReferralsByClientAndProgram(Long clientId, Long programId);

    /**
     * Retrieves a single client referral by its unique identifier.
     *
     * @param id Long the referral ID
     * @return ClientReferral the referral record, or {@code null} if not found
     * @throws IllegalArgumentException if id is {@code null} or not positive
     */
    public ClientReferral getClientReferral(Long id);

    /**
     * Saves or updates a client referral record.
     *
     * @param referral ClientReferral the referral entity to persist
     * @throws IllegalArgumentException if referral is {@code null}
     */
    public void saveClientReferral(ClientReferral referral);

    /**
     * Searches for referrals matching the criteria in the provided referral object.
     *
     * <p>If the referral has a valid program ID, results are filtered by that program;
     * otherwise all referrals are returned.</p>
     *
     * @param referral ClientReferral the search criteria
     * @return List&lt;ClientReferral&gt; matching referral records
     */
    public List<ClientReferral> search(ClientReferral referral);

    /**
     * Retrieves all client referrals associated with a specific program.
     *
     * @param programId int the program ID to filter by
     * @return List&lt;ClientReferral&gt; referrals for the specified program
     */
    public List<ClientReferral> getClientReferralsByProgram(int programId);

}
 