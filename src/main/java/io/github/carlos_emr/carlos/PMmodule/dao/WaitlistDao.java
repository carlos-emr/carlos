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
package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.Collection;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.wlmatch.CriteriasBO;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.MatchBO;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.VacancyDisplayBO;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.match.client.ClientData;
import io.github.carlos_emr.carlos.match.vacancy.VacancyData;

/**
 * Data access interface for waitlist management operations within the
 * Program Management module.
 *
 * <p>Provides methods for matching clients to vacancies, querying vacancy
 * display data, loading client intake data from eforms, and retrieving
 * vacancy criteria data for the waitlist matching engine.</p>
 *
 * @since 2001-09-17
 * @see WaitlistDaoImpl
 * @see VacancyDisplayBO
 * @see MatchBO
 */
public interface WaitlistDao {

    /**
     * Retrieves client matches for a vacancy, ordered by match percentage descending.
     *
     * @param vacancyId int the vacancy ID
     * @return List&lt;MatchBO&gt; client match records with demographic and waitlist data
     */
    public List<MatchBO> getClientMatches(int vacancyId);

    /**
     * Retrieves client matches for a vacancy that meet a minimum match percentage.
     *
     * @param vacancyId int the vacancy ID
     * @param percentage double the minimum match percentage threshold
     * @return List&lt;MatchBO&gt; filtered client match records
     */
    public List<MatchBO> getClientMatchesWithMinPercentage(int vacancyId, double percentage);

    /**
     * Searches for eform data records matching the specified criteria.
     *
     * <p>Supports single-value, range, and multi-value criteria types.
     * Returns the most recent eform per demographic when duplicates exist.</p>
     *
     * @param crits CriteriasBO the search criteria collection
     * @return Collection&lt;EFormData&gt; matching eform data records
     */
    public Collection<EFormData> searchForMatchingEforms(CriteriasBO crits);

    /**
     * Lists active vacancy display records for a specific waitlist program.
     *
     * @param programID int the waitlist program ID
     * @return List&lt;VacancyDisplayBO&gt; vacancy display data
     */
    public List<VacancyDisplayBO> listDisplayVacanciesForWaitListProgram(int programID);

    /**
     * Lists active vacancy display records for all waitlist programs.
     *
     * @return List&lt;VacancyDisplayBO&gt; vacancy display data ordered by vacancy ID
     */
    public List<VacancyDisplayBO> listDisplayVacanciesForAllWaitListPrograms();

    /**
     * Retrieves active vacancy display records for an agency program.
     *
     * @param programID int the agency program ID
     * @return List&lt;VacancyDisplayBO&gt; vacancy display data for the program
     */
    public List<VacancyDisplayBO> getDisplayVacanciesForAgencyProgram(int programID);

    /**
     * Retrieves display data for a single vacancy, including match statistics.
     *
     * @param vacancyID int the vacancy ID
     * @return VacancyDisplayBO the vacancy display data, or {@code null} if not found
     */
    public VacancyDisplayBO getDisplayVacancy(int vacancyID);

    /**
     * Loads match statistics (accepted, rejected, pending counts) into a vacancy display object.
     *
     * @param bo VacancyDisplayBO the display object to populate with statistics
     */
    public void loadStats(VacancyDisplayBO bo);

    /**
     * Retrieves the waitlist program ID associated with a vacancy.
     *
     * @param vacancyId int the vacancy ID
     * @return Integer the program ID, or {@code null} if the vacancy is not found
     */
    public Integer getProgramIdByVacancyId(int vacancyId);

    /**
     * Lists the count of active vacancies grouped by waitlist program.
     *
     * @return List&lt;VacancyDisplayBO&gt; vacancy counts per program
     */
    public List<VacancyDisplayBO> listNoOfVacanciesForWaitListProgram();

    /**
     * Lists all active vacancies with their template names, ordered by vacancy name.
     *
     * @return List&lt;VacancyDisplayBO&gt; active vacancies
     */
    public List<VacancyDisplayBO> listVacanciesForWaitListProgram();

    /**
     * Retrieves intake eform data for all clients without referrals,
     * mapped to criteria fields for waitlist matching.
     *
     * @return List&lt;ClientData&gt; client data records with mapped criteria values
     */
    public List<ClientData> getAllClientsData();

    /**
     * Retrieves intake eform data for clients referred to a specific waitlist program,
     * mapped to criteria fields for waitlist matching.
     *
     * @param wlProgramId int the waitlist program ID
     * @return List&lt;ClientData&gt; client data records with mapped criteria values
     */
    public List<ClientData> getAllClientsDataByProgramId(int wlProgramId);

    /**
     * Retrieves intake eform data for a specific client, mapped to criteria fields.
     *
     * @param clientId int the demographic ID of the client
     * @return ClientData the client data with mapped criteria values
     */
    public ClientData getClientData(int clientId);

    /**
     * Loads vacancy criteria data for a specific vacancy, including field types,
     * values, ranges, and multi-select options.
     *
     * @param vacancyId int the vacancy ID
     * @return VacancyData the vacancy criteria data
     */
    public VacancyData loadVacancyData(final int vacancyId);

    /**
     * Loads vacancy criteria data for a specific vacancy within a waitlist program.
     *
     * @param vacancyId int the vacancy ID
     * @param wlProgramId int the waitlist program ID
     * @return VacancyData the vacancy criteria data
     */
    public VacancyData loadVacancyData(final int vacancyId, final int wlProgramId);

}
