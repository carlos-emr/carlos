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
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Drug;

/**
 * DAO interface for drug and prescription operations.
 *
 * @since 2001
 */

public interface DrugDao extends AbstractDao<Drug> {

    /**
     * Add New Drug.
     *
     * @param d Drug the d
     * @return boolean
     */
    public boolean addNewDrug(Drug d);

    /**
     * Find By Prescription Id.
     *
     * @param prescriptionId Integer the prescriptionId
     * @return List<Drug>
     */
    public List<Drug> findByPrescriptionId(Integer prescriptionId);

    /**
     * Find By Demographic Id.
     *
     * @param demographicId Integer the demographicId
     * @return List<Drug>
     */
    public List<Drug> findByDemographicId(Integer demographicId);

    /**
     * Find By Demographic Id.
     *
     * @param demographicId Integer the demographicId
     * @param archived Boolean the archived
     * @return List<Drug>
     */
    public List<Drug> findByDemographicId(Integer demographicId, Boolean archived);

    /**
     * Find By Script No.
     *
     * @param scriptNo Integer the scriptNo
     * @param archived Boolean the archived
     * @return List<Drug>
     */
    public List<Drug> findByScriptNo(Integer scriptNo, Boolean archived);

    /**
     * Find By Demographic Id Order By Date.
     *
     * @param demographicId Integer the demographicId
     * @param archived Boolean the archived
     * @return List<Drug>
     */
    public List<Drug> findByDemographicIdOrderByDate(Integer demographicId, Boolean archived);

    /**
     * Find By Demographic Id Order By Position For Export.
     *
     * @param demographicId Integer the demographicId
     * @param archived Boolean the archived
     * @return List<Drug>
     */
    public List<Drug> findByDemographicIdOrderByPositionForExport(Integer demographicId, Boolean archived);

    /**
     * Find By Demographic Id Order By Position.
     *
     * @param demographicId Integer the demographicId
     * @param archived Boolean the archived
     * @return List<Drug>
     */
    public List<Drug> findByDemographicIdOrderByPosition(Integer demographicId, Boolean archived);

    public List<Drug> findByDemographicIdSimilarDrugOrderByDate(Integer demographicId, String regionalIdentifier,
                                                                String customName);

    public List<Drug> findByDemographicIdSimilarDrugOrderByDate(Integer demographicId, String regionalIdentifier,
                                                                String customName, String brandName);

    public List<Drug> findByDemographicIdSimilarDrugOrderByDate(Integer demographicId, String regionalIdentifier,
                                                                String customName, String brandName, String atc);

    /**
     * Get Unique Prescriptions.
     *
     * @param demographic_no String the demographic_no
     * @return List<Drug>
     */
    public List<Drug> getUniquePrescriptions(String demographic_no);

    /**
     * Get Prescriptions.
     *
     * @param demographic_no String the demographic_no
     * @return List<Drug>
     */
    public List<Drug> getPrescriptions(String demographic_no);

    /**
     * Get Prescriptions.
     *
     * @param demographic_no String the demographic_no
     * @param all boolean the all
     * @return List<Drug>
     */
    public List<Drug> getPrescriptions(String demographic_no, boolean all);

    public int getNumberOfDemographicsWithRxForProvider(String providerNo, Date startDate, Date endDate,
                                                        boolean distinct);

    /**
     * Find By Demographic Id Updated After Date.
     *
     * @param demographicId Integer the demographicId
     * @param updatedAfterThisDate Date the updatedAfterThisDate
     * @return List<Drug>
     */
    public List<Drug> findByDemographicIdUpdatedAfterDate(Integer demographicId, Date updatedAfterThisDate);

    /**
     * Find By Atc.
     *
     * @param atc String the atc
     * @return List<Drug>
     */
    public List<Drug> findByAtc(String atc);

    /**
     * Find By Atc.
     *
     * @param atc List<String> the atc
     * @return List<Drug>
     */
    public List<Drug> findByAtc(List<String> atc);

    /**
     * Find By Demographic Id And Atc.
     *
     * @param demographicNo int the demographicNo
     * @param atc String the atc
     * @return List<Drug>
     */
    public List<Drug> findByDemographicIdAndAtc(int demographicNo, String atc);

    /**
     * Find By Demographic Id And Region.
     *
     * @param demographicNo int the demographicNo
     * @param regionalIdentifier String the regionalIdentifier
     * @return List<Drug>
     */
    public List<Drug> findByDemographicIdAndRegion(int demographicNo, String regionalIdentifier);

    /**
     * Find By Demographic Id And Drug Id.
     *
     * @param demographicNo int the demographicNo
     * @param drugId Integer the drugId
     * @return List<Drug>
     */
    public List<Drug> findByDemographicIdAndDrugId(int demographicNo, Integer drugId);

    /**
     * Find Drugs And Prescriptions.
     *
     * @param demographicNo int the demographicNo
     * @return List<Object[]>
     */
    public List<Object[]> findDrugsAndPrescriptions(int demographicNo);

    /**
     * Find Drugs And Prescriptions By Script Number.
     *
     * @param scriptNumber int the scriptNumber
     * @return List<Object[]>
     */
    public List<Object[]> findDrugsAndPrescriptionsByScriptNumber(int scriptNumber);

    /**
     * Get Max Position.
     *
     * @param demographicNo int the demographicNo
     * @return int
     */
    public int getMaxPosition(int demographicNo);

    public Drug findByEverything(String providerNo, int demographicNo, Date rxDate, Date endDate, Date writtenDate,
                                 String brandName, String gcn_SEQNO, String customName, float takeMin, float takeMax, String frequencyCode,
                                 String duration, String durationUnit, String quantity, String unitName, int repeat, Date lastRefillDate,
                                 boolean nosubs, boolean prn, String escapedSpecial, String outsideProviderName, String outsideProviderOhip,
                                 boolean customInstr, Boolean longTerm, boolean customNote, Boolean pastMed,
                                 Boolean patientCompliance, String specialInstruction, String comment, boolean startDateUnknown);

    /**
     * Find By Parameter.
     *
     * @param parameter String the parameter
     * @param value String the value
     * @return List<Object[]>
     */
    public List<Object[]> findByParameter(String parameter, String value);

    public List<Drug> findByRegionBrandDemographicAndProvider(String regionalIdentifier, String brandName,
                                                              int demographicNo, String providerNo);

    /**
     * Find By Brand Name Demographic And Provider.
     *
     * @param brandName String the brandName
     * @param demographicNo int the demographicNo
     * @param providerNo String the providerNo
     * @return Drug
     */
    public Drug findByBrandNameDemographicAndProvider(String brandName, int demographicNo, String providerNo);

    /**
     * Find By Custom Name Demographic Id And Provider No.
     *
     * @param customName String the customName
     * @param demographicNo int the demographicNo
     * @param providerNo String the providerNo
     * @return Drug
     */
    public Drug findByCustomNameDemographicIdAndProviderNo(String customName, int demographicNo, String providerNo);

    /**
     * Find Last Not Archived Id.
     *
     * @param brandName String the brandName
     * @param genericName String the genericName
     * @param demographicNo int the demographicNo
     * @return Integer
     */
    public Integer findLastNotArchivedId(String brandName, String genericName, int demographicNo);

    public Drug findByDemographicIdRegionalIdentifierAndAtcCode(String atcCode, String regionalIdentifier,
                                                                int demographicNo);

    /**
     * Find Special Instructions.
     * @return List<String>
     */
    public List<String> findSpecialInstructions();

    /**
     * Find Special Instructions Matching.
     *
     * @param spInstructQuery String the spInstructQuery
     * @return List<String>
     */
    public List<String> findSpecialInstructionsMatching(String spInstructQuery);

    /**
     * Find Demographic Ids Updated After Date.
     *
     * @param updatedAfterThisDate Date the updatedAfterThisDate
     * @return List<Integer>
     */
    public List<Integer> findDemographicIdsUpdatedAfterDate(Date updatedAfterThisDate);

    /**
     * Find New Drugs Since Demo Key.
     *
     * @param keyName String the keyName
     * @return List<Integer>
     */
    public List<Integer> findNewDrugsSinceDemoKey(String keyName);

    /**
     * Find Long Term Drugs By Demographic.
     *
     * @param demographicId Integer the demographicId
     * @return List<Drug>
     */
    public List<Drug> findLongTermDrugsByDemographic(Integer demographicId);

}
