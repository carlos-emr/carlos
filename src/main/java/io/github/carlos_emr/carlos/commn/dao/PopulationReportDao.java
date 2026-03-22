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
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.Map;
import java.util.SortedSet;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.EncounterUtil.EncounterType;

/**
 * DAO interface for population reporting operations.
 *
 * @since 2001
 */

public interface PopulationReportDao {
    public static final int LOW = 0;
    public static final int MEDIUM = 1;
    public static final int HIGH = 2;

    /**
     * Get Current Population Size.
     * @return int
     */
    int getCurrentPopulationSize();

    /**
     * Get Current And Historical Population Size.
     *
     * @param numYears int the numYears
     * @return int
     */
    int getCurrentAndHistoricalPopulationSize(int numYears);

    /**
     * Get Usages.
     *
     * @param numYears int the numYears
     * @return int[]
     */
    int[] getUsages(int numYears);

    /**
     * Get Mortalities.
     *
     * @param numYears int the numYears
     * @return int
     */
    int getMortalities(int numYears);

    /**
     * Get Prevalence.
     *
     * @param icd10Codes SortedSet<String> the icd10Codes
     * @return int
     */
    int getPrevalence(SortedSet<String> icd10Codes);

    /**
     * Get Incidence.
     *
     * @param icd10Codes SortedSet<String> the icd10Codes
     * @return int
     */
    int getIncidence(SortedSet<String> icd10Codes);

    /**
     * Get Case Management Note Count Grouped By Issue Group.
     *
     * @param programId int the programId
     * @param roleId Integer the roleId
     * @param encounterType EncounterType the encounterType
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return Map<Integer, Integer>
     */
    Map<Integer, Integer> getCaseManagementNoteCountGroupedByIssueGroup(int programId, Integer roleId, EncounterType encounterType, Date startDate, Date endDate);

    /**
     * Get Case Management Note Count Grouped By Issue Group.
     *
     * @param programId int the programId
     * @param provider Provider the provider
     * @param encounterType EncounterType the encounterType
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return Map<Integer, Integer>
     */
    Map<Integer, Integer> getCaseManagementNoteCountGroupedByIssueGroup(int programId, Provider provider, EncounterType encounterType, Date startDate, Date endDate);

    /**
     * Get Case Management Note Total Unique Encounter Count In Issue Groups.
     *
     * @param programId int the programId
     * @param roleId Integer the roleId
     * @param encounterType EncounterType the encounterType
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return Integer
     */
    Integer getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(int programId, Integer roleId, EncounterType encounterType, Date startDate, Date endDate);

    /**
     * Get Case Management Note Total Unique Encounter Count In Issue Groups.
     *
     * @param programId int the programId
     * @param provider Provider the provider
     * @param encounterType EncounterType the encounterType
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return Integer
     */
    Integer getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(int programId, Provider provider, EncounterType encounterType, Date startDate, Date endDate);

    /**
     * Get Case Management Note Total Unique Client Count In Issue Groups.
     *
     * @param programId int the programId
     * @param roleId Integer the roleId
     * @param encounterType EncounterType the encounterType
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return Integer
     */
    Integer getCaseManagementNoteTotalUniqueClientCountInIssueGroups(int programId, Integer roleId, EncounterType encounterType, Date startDate, Date endDate);

    /**
     * Get Case Management Note Total Unique Client Count In Issue Groups.
     *
     * @param programId int the programId
     * @param provider Provider the provider
     * @param encounterType EncounterType the encounterType
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return Integer
     */
    Integer getCaseManagementNoteTotalUniqueClientCountInIssueGroups(int programId, Provider provider, EncounterType encounterType, Date startDate, Date endDate);

    /**
     * Get Case Management Note Count By Issue Group.
     *
     * @param programId int the programId
     * @param issueGroupId Integer the issueGroupId
     * @param roleId Integer the roleId
     * @param encounterType EncounterType the encounterType
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return Integer
     */
    Integer getCaseManagementNoteCountByIssueGroup(int programId, Integer issueGroupId, Integer roleId, EncounterType encounterType, Date startDate, Date endDate);
}
