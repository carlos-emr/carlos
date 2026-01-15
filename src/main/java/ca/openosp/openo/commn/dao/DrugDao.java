//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.commn.dao;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ca.openosp.openo.commn.model.Drug;

public interface DrugDao extends AbstractDao<Drug> {

    public boolean addNewDrug(Drug d);

    public List<Drug> findByPrescriptionId(Integer prescriptionId);

    public List<Drug> findByDemographicId(Integer demographicId);

    public List<Drug> findByDemographicId(Integer demographicId, Boolean archived);

    public List<Drug> findByScriptNo(Integer scriptNo, Boolean archived);

    public List<Drug> findByDemographicIdOrderByDate(Integer demographicId, Boolean archived);

    public List<Drug> findByDemographicIdOrderByPositionForExport(Integer demographicId, Boolean archived);

    public List<Drug> findByDemographicIdOrderByPosition(Integer demographicId, Boolean archived);

    public List<Drug> findByDemographicIdSimilarDrugOrderByDate(Integer demographicId, String regionalIdentifier,
                                                                String customName);

    public List<Drug> findByDemographicIdSimilarDrugOrderByDate(Integer demographicId, String regionalIdentifier,
                                                                String customName, String brandName);

    public List<Drug> findByDemographicIdSimilarDrugOrderByDate(Integer demographicId, String regionalIdentifier,
                                                                String customName, String brandName, String atc);

    public List<Drug> getUniquePrescriptions(String demographic_no);

    public List<Drug> getPrescriptions(String demographic_no);

    public List<Drug> getPrescriptions(String demographic_no, boolean all);

    public int getNumberOfDemographicsWithRxForProvider(String providerNo, Date startDate, Date endDate,
                                                        boolean distinct);

    public List<Drug> findByDemographicIdUpdatedAfterDate(Integer demographicId, Date updatedAfterThisDate);

    public List<Drug> findByAtc(String atc);

    public List<Drug> findByAtc(List<String> atc);

    public List<Drug> findByDemographicIdAndAtc(int demographicNo, String atc);

    public List<Drug> findByDemographicIdAndRegion(int demographicNo, String regionalIdentifier);

    public List<Drug> findByDemographicIdAndDrugId(int demographicNo, Integer drugId);

    public List<Object[]> findDrugsAndPrescriptions(int demographicNo);

    public List<Object[]> findDrugsAndPrescriptionsByScriptNumber(int scriptNumber);

    public int getMaxPosition(int demographicNo);

    public Drug findByEverything(String providerNo, int demographicNo, Date rxDate, Date endDate, Date writtenDate,
                                 String brandName, int gcn_SEQNO, String customName, float takeMin, float takeMax, String frequencyCode,
                                 String duration, String durationUnit, String quantity, String unitName, int repeat, Date lastRefillDate,
                                 boolean nosubs, boolean prn, String escapedSpecial, String outsideProviderName, String outsideProviderOhip,
                                 boolean customInstr, Boolean longTerm, boolean customNote, Boolean pastMed,
                                 Boolean patientCompliance, String specialInstruction, String comment, boolean startDateUnknown);

    public List<Object[]> findByParameter(String parameter, String value);

    public List<Drug> findByRegionBrandDemographicAndProvider(String regionalIdentifier, String brandName,
                                                              int demographicNo, String providerNo);

    public Drug findByBrandNameDemographicAndProvider(String brandName, int demographicNo, String providerNo);

    public Drug findByCustomNameDemographicIdAndProviderNo(String customName, int demographicNo, String providerNo);

    public Integer findLastNotArchivedId(String brandName, String genericName, int demographicNo);

    public Drug findByDemographicIdRegionalIdentifierAndAtcCode(String atcCode, String regionalIdentifier,
                                                                int demographicNo);

    public List<String> findSpecialInstructions();

    public List<String> findSpecialInstructionsMatching(String spInstructQuery);

    public List<Integer> findDemographicIdsUpdatedAfterDate(Date updatedAfterThisDate);

    public List<Integer> findNewDrugsSinceDemoKey(String keyName);

    public List<Drug> findLongTermDrugsByDemographic(Integer demographicId);

}
