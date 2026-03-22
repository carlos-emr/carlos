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

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

import io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientSearchFormBean;
import io.github.carlos_emr.carlos.commn.Gender;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchResult;
import org.springframework.context.ApplicationEventPublisher;

/**
 * DAO interface for patient demographic data access operations.
 * <p>
 * Provides comprehensive operations for managing patient demographics including
 * search, retrieval, creation, and updates. Supports merged demographic queries,
 * Health Insurance Number (HIN) lookups, provider-based patient lists, and
 * integration with the CAISI community module.
 *
 * @since 2005
 */
public interface DemographicDao {

    /**
     * Get Merged Demographics.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<Integer>
     */
    public List<Integer> getMergedDemographics(Integer demographicNo);

    /**
     * Get Demographic.
     *
     * @param demographic_no String the demographic_no
     * @return Demographic
     */
    public Demographic getDemographic(String demographic_no);

    /**
     * Get Demographics.
     * @return List
     */
    public List getDemographics();

    /**
     * Get Demographics.
     *
     * @param demographicIds List<Integer> the demographicIds
     * @return List<Demographic>
     */
    public List<Demographic> getDemographics(List<Integer> demographicIds);

    /**
     * Get Active Demographic Count.
     * @return Long
     */
    public Long getActiveDemographicCount();

    /**
     * Get Active Demographics.
     *
     * @param offset final int the offset
     * @param limit final int the limit
     * @return List<Demographic>
     */
    public List<Demographic> getActiveDemographics(final int offset, final int limit);

    /**
     * Get Demographic By Id.
     *
     * @param demographic_id Integer the demographic_id
     * @return Demographic
     */
    public Demographic getDemographicById(Integer demographic_id);

    /**
     * Get Demographic By Provider.
     *
     * @param providerNo String the providerNo
     * @return List<Demographic>
     */
    public List<Demographic> getDemographicByProvider(String providerNo);

    /**
     * Get Demographic By Provider.
     *
     * @param providerNo String the providerNo
     * @param onlyActive boolean the onlyActive
     * @return List<Demographic>
     */
    public List<Demographic> getDemographicByProvider(String providerNo, boolean onlyActive);

    /**
     * Get Demographic Nos By Provider.
     *
     * @param providerNo String the providerNo
     * @param onlyActive boolean the onlyActive
     * @return List<Integer>
     */
    public List<Integer> getDemographicNosByProvider(String providerNo, boolean onlyActive);


    /**
     * Get Active Demographic By Program.
     *
     * @param programId int the programId
     * @param dt Date the dt
     * @param defdt Date the defdt
     * @return List
     */
    public List getActiveDemographicByProgram(int programId, Date dt, Date defdt);

    /**
     * Get Active Demos By Health Card No.
     *
     * @param hcn String the hcn
     * @param hcnType String the hcnType
     * @return List<Demographic>
     */
    public List<Demographic> getActiveDemosByHealthCardNo(String hcn, String hcnType);

    /**
     * Get Archive Demographic By Program Optimized.
     *
     * @param programId int the programId
     * @param dt Date the dt
     * @param defdt Date the defdt
     * @return Set
     */
    public Set getArchiveDemographicByProgramOptimized(int programId, Date dt, Date defdt);

    /**
     * Get Program Id By Demo No.
     *
     * @param demoNo Integer the demoNo
     * @return List
     */
    public List getProgramIdByDemoNo(Integer demoNo);

    /**
     * Clear.
     */
    public void clear();

    /**
     * Get Demo Program.
     *
     * @param demoNo Integer the demoNo
     * @return List
     */
    public List getDemoProgram(Integer demoNo);

    /**
     * Get Demo Program Current.
     *
     * @param demoNo Integer the demoNo
     * @return List
     */
    public List getDemoProgramCurrent(Integer demoNo);

    /**
     * Get Demographic Ids Admitted Into Facility.
     *
     * @param facilityId int the facilityId
     * @return List<Integer>
     */
    public List<Integer> getDemographicIdsAdmittedIntoFacility(int facilityId);

    /**
     * Search Demographic.
     *
     * @param searchStr String the searchStr
     * @return List<Demographic>
     */
    public List<Demographic> searchDemographic(String searchStr);

    /**
     * Search Demographic By Name String.
     *
     * @param searchString String the searchString
     * @param startIndex int the startIndex
     * @param itemsToReturn int the itemsToReturn
     * @return List<Demographic>
     */
    public List<Demographic> searchDemographicByNameString(String searchString, int startIndex, int itemsToReturn);

    public List<Demographic> searchDemographicByName(String searchStr, int limit, int offset, String providerNo,
                                                     boolean outOfDomain);

    public List<Demographic> searchDemographicByNameAndNotStatus(String searchStr, List<String> statuses, int limit,
                                                                 int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByNameAndStatus(String searchStr, List<String> statuses, int limit,
                                                              int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByName(String searchStr, int limit, int offset, String orderBy,
                                                     String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByNameAndNotStatus(String searchStr, List<String> statuses, int limit,
                                                                 int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByNameAndStatus(String searchStr, List<String> statuses, int limit,
                                                              int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByNameAndStatus(String searchStr, List<String> statuses, int limit,
                                                              int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses);

    public List<Demographic> searchDemographicByNameAndStatus(String searchStr, List<String> statuses, int limit,
                                                              int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses,
                                                              boolean ignoreMerged);

    public List<Demographic> searchMergedDemographicByName(String searchStr, int limit, int offset, String providerNo,
                                                           boolean outOfDomain);

    public List<Demographic> searchDemographicByDOB(String dobStr, int limit, int offset, String providerNo,
                                                    boolean outOfDomain);

    public List<Demographic> searchDemographicByDOBWithMerged(String dobStr, int limit, int offset, String providerNo,
                                                              boolean outOfDomain);

    /**
     * Get By Hin And Gender And Dob And Last Name.
     *
     * @param hin String the hin
     * @param gender String the gender
     * @param dob String the dob
     * @param lastName String the lastName
     * @return List<Demographic>
     */
    public List<Demographic> getByHinAndGenderAndDobAndLastName(String hin, String gender, String dob, String lastName);

    public List<Demographic> searchDemographicByDOBAndNotStatus(String dobStr, List<String> statuses, int limit,
                                                                int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByDOBAndStatus(String dobStr, List<String> statuses, int limit,
                                                             int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByDOB(String dobStr, int limit, int offset, String orderBy,
                                                    String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByDOBAndNotStatus(String dobStr, List<String> statuses, int limit,
                                                                int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByDOBAndStatus(String dobStr, List<String> statuses, int limit,
                                                             int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByDOBAndStatus(String dobStr, List<String> statuses, int limit,
                                                             int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses);

    public List<Demographic> searchDemographicByDOBAndStatus(String dobStr, List<String> statuses, int limit,
                                                             int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses,
                                                             boolean ignoreMerged);

    public List<Demographic> searchMergedDemographicByDOB(String dobStr, int limit, int offset, String providerNo,
                                                          boolean outOfDomain);

    public List<Demographic> searchDemographicByPhone(String phoneStr, int limit, int offset, String providerNo,
                                                      boolean outOfDomain);

    public List<Demographic> searchDemographicByPhoneAndNotStatus(String phoneStr, List<String> statuses, int limit,
                                                                  int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByPhoneAndStatus(String phoneStr, List<String> statuses, int limit,
                                                               int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByPhone(String phoneStr, int limit, int offset, String orderBy,
                                                      String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByPhoneAndNotStatus(String phoneStr, List<String> statuses, int limit,
                                                                  int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByPhoneAndStatus(String phoneStr, List<String> statuses, int limit,
                                                               int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByPhoneAndStatus(String phoneStr, List<String> statuses, int limit,
                                                               int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses);

    public List<Demographic> searchDemographicByPhoneAndStatus(String phoneStr, List<String> statuses, int limit,
                                                               int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses,
                                                               boolean ignoreMerged);

    public List<Demographic> searchMergedDemographicByPhone(String phoneStr, int limit, int offset, String providerNo,
                                                            boolean outOfDomain);

    /**
     * Search Demographic By H I N.
     *
     * @param hinStr String the hinStr
     * @return List<Demographic>
     */
    public List<Demographic> searchDemographicByHIN(String hinStr);

    public List<Demographic> searchDemographicByHIN(String hinStr, int limit, int offset, String providerNo,
                                                    boolean outOfDomain);

    public List<Demographic> searchDemographicByHINAndNotStatus(String hinStr, List<String> statuses, int limit,
                                                                int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByHINAndStatus(String hinStr, List<String> statuses, int limit,
                                                             int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByHIN(String hinStr, int limit, int offset, String orderBy,
                                                    String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByHINAndNotStatus(String hinStr, List<String> statuses, int limit,
                                                                int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByHINAndStatus(String hinStr, List<String> statuses, int limit,
                                                             int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByHINAndStatus(String hinStr, List<String> statuses, int limit,
                                                             int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses);

    public List<Demographic> searchDemographicByHINAndStatus(String hinStr, List<String> statuses, int limit,
                                                             int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses,
                                                             boolean ignoreMerged);

    public List<Demographic> findByAttributes(
            String hin,
            String firstName,
            String lastName,
            Gender gender,
            Calendar dateOfBirth,
            String city,
            String province,
            String phone,
            String email,
            String alias,
            int startIndex,
            int itemsToReturn);

    public List<Demographic> findByAttributes(
            String hin,
            String firstName,
            String lastName,
            Gender gender,
            Calendar dateOfBirth,
            String city,
            String province,
            String phone,
            String email,
            String alias,
            int startIndex,
            int itemsToReturn,
            boolean orderByName);

    public List<Demographic> searchMergedDemographicByHIN(String hinStr, int limit, int offset, String providerNo,
                                                          boolean outOfDomain);

    public List<Demographic> searchDemographicByAddress(String addressStr, int limit, int offset, String providerNo,
                                                        boolean outOfDomain);

    public List<Demographic> searchDemographicByAddressAndStatus(String addressStr, List<String> statuses, int limit,
                                                                 int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByAddressAndNotStatus(String addressStr, List<String> statuses, int limit,
                                                                    int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByAddress(String addressStr, int limit, int offset, String orderBy,
                                                        String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByAddressAndStatus(String addressStr, List<String> statuses, int limit,
                                                                 int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByAddressAndNotStatus(String addressStr, List<String> statuses, int limit,
                                                                    int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByAddressAndStatus(String addressStr, List<String> statuses, int limit,
                                                                 int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses);

    public List<Demographic> searchDemographicByAddressAndStatus(String addressStr, List<String> statuses, int limit,
                                                                 int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses,
                                                                 boolean ignoreMerged);

    public List<Demographic> searchDemographicByExtKeyAndValueLike(DemographicExt.DemographicProperty key, String value,
                                                                   int limit, int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByExtKeyAndValueLikeAndNotStatus(DemographicExt.DemographicProperty key,
                                                                               String value, List<String> statuses, int limit, int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByExtKeyAndValueLikeAndStatus(DemographicExt.DemographicProperty key,
                                                                            String value, List<String> statuses, int limit, int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByExtKeyAndValueLike(DemographicExt.DemographicProperty key, String value,
                                                                   int limit, int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByExtKeyAndValueLikeWithMerged(DemographicExt.DemographicProperty key,
                                                                             String value, int limit, int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> searchDemographicByExtKeyAndValueLikeAndNotStatus(DemographicExt.DemographicProperty key,
                                                                               String value, List<String> statuses, int limit, int offset, String orderBy, String providerNo,
                                                                               boolean outOfDomain);

    public List<Demographic> searchDemographicByExtKeyAndValueLikeAndStatus(DemographicExt.DemographicProperty key,
                                                                            String value, List<String> statuses, int limit, int offset, String orderBy, String providerNo,
                                                                            boolean outOfDomain);

    public List<Demographic> searchDemographicByExtKeyAndValueLikeAndStatus(DemographicExt.DemographicProperty key,
                                                                            String value, List<String> statuses, int limit, int offset, String orderBy, String providerNo,
                                                                            boolean outOfDomain, boolean ignoreStatuses);

    public List<Demographic> searchDemographicByExtKeyAndValueLikeAndStatus(DemographicExt.DemographicProperty key,
                                                                            String value, List<String> statuses, int limit, int offset, String orderBy, String providerNo,
                                                                            boolean outOfDomain, boolean ignoreStatuses, boolean ignoreMerged);

    public List<Demographic> searchMergedDemographicByAddress(String addressStr, int limit, int offset,
                                                              String providerNo, boolean outOfDomain);

    public List<Demographic> findDemographicByChartNo(String chartNoStr, int limit, int offset, String providerNo,
                                                      boolean outOfDomain);

    public List<Demographic> findDemographicByChartNoAndStatus(String chartNoStr, List<String> statuses, int limit,
                                                               int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> findDemographicByChartNoAndNotStatus(String chartNoStr, List<String> statuses, int limit,
                                                                  int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> findDemographicByChartNo(String chartNoStr, int limit, int offset, String orderBy,
                                                      String providerNo, boolean outOfDomain);

    public List<Demographic> findDemographicByChartNoAndStatus(String chartNoStr, List<String> statuses, int limit,
                                                               int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> findDemographicByChartNoAndNotStatus(String chartNoStr, List<String> statuses, int limit,
                                                                  int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> findDemographicByChartNoAndStatus(String chartNoStr, List<String> statuses, int limit,
                                                               int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses);

    public List<Demographic> findDemographicByDemographicNo(String demographicNoStr, int limit, int offset,
                                                            String providerNo, boolean outOfDomain);

    public List<Demographic> findDemographicByDemographicNoAndStatus(String demographicNoStr, List<String> statuses,
                                                                     int limit, int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> findDemographicByDemographicNoAndNotStatus(String demographicNoStr, List<String> statuses,
                                                                        int limit, int offset, String providerNo, boolean outOfDomain);

    public List<Demographic> findDemographicByDemographicNo(String demographicNoStr, int limit, int offset,
                                                            String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> findDemographicByDemographicNoAndStatus(String demographicNoStr, List<String> statuses,
                                                                     int limit, int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> findDemographicByDemographicNoAndNotStatus(String demographicNoStr, List<String> statuses,
                                                                        int limit, int offset, String orderBy, String providerNo, boolean outOfDomain);

    public List<Demographic> findDemographicByDemographicNoAndStatus(String demographicNoStr, List<String> statuses,
                                                                     int limit, int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses);

    /**
     * Save.
     *
     * @param demographic Demographic the demographic
     */
    public void save(Demographic demographic);

    /**
     * Get Order Field.
     *
     * @param orderBy String the orderBy
     * @param nativeQuery boolean the nativeQuery
     * @return String
     */
    public String getOrderField(String orderBy, boolean nativeQuery);

    /**
     * Get Order Field.
     *
     * @param orderBy String the orderBy
     * @return String
     */
    public String getOrderField(String orderBy);

    /**
     * Get Demographic Ids Altered Since Time.
     *
     * @param value Date the value
     * @return List<Integer>
     */
    public List<Integer> getDemographicIdsAlteredSinceTime(Date value);

    /**
     * Get Demographic Ids Opened Chart Since Time.
     *
     * @param value String the value
     * @return List<Integer>
     */
    public List<Integer> getDemographicIdsOpenedChartSinceTime(String value);

    /**
     * Get Roster Statuses.
     * @return List<String>
     */
    public List<String> getRosterStatuses();

    /**
     * Get All Roster Statuses.
     * @return List<String>
     */
    public List<String> getAllRosterStatuses();

    /**
     * Get All Patient Statuses.
     * @return List<String>
     */
    public List<String> getAllPatientStatuses();

    /**
     * Search_ptstatus.
     * @return List<String>
     */
    public List<String> search_ptstatus();

    /**
     * Get All Provider Numbers.
     * @return List<String>
     */
    public List<String> getAllProviderNumbers();

    /**
     * Client Exists.
     *
     * @param demographicNo Integer the demographicNo
     * @return boolean
     */
    public boolean clientExists(Integer demographicNo);

    /**
     * Client Exists Then Evict.
     *
     * @param demographicNo Integer the demographicNo
     * @return boolean
     */
    public boolean clientExistsThenEvict(Integer demographicNo);

    /**
     * Get Client By Demographic No.
     *
     * @param demographicNo Integer the demographicNo
     * @return Demographic
     */
    public Demographic getClientByDemographicNo(Integer demographicNo);

    /**
     * Get Clients.
     * @return List<Demographic>
     */
    public List<Demographic> getClients();

    /**
     * Search.
     *
     * @param bean ClientSearchFormBean the bean
     * @param returnOptinsOnly boolean the returnOptinsOnly
     * @param excludeMerged boolean the excludeMerged
     * @return List<Demographic>
     */
    public List<Demographic> search(ClientSearchFormBean bean, boolean returnOptinsOnly, boolean excludeMerged);

    /**
     * Search.
     *
     * @param bean ClientSearchFormBean the bean
     * @return List<Demographic>
     */
    public List<Demographic> search(ClientSearchFormBean bean);

    /**
     * Save Client.
     *
     * @param client Demographic the client
     */
    public void saveClient(Demographic client);

    // public Map<String, ClientListsReportResults>
    // findByReportCriteria(ClientListsReportFormBean x);

    /**
     * Get Clients By Chart No.
     *
     * @param chartNo String the chartNo
     * @return List<Demographic>
     */
    public List<Demographic> getClientsByChartNo(String chartNo);

    /**
     * Get Clients By Health Card.
     *
     * @param num String the num
     * @param type String the type
     * @return List<Demographic>
     */
    public List<Demographic> getClientsByHealthCard(String num, String type);

    /**
     * Search By Health Card.
     *
     * @param hin String the hin
     * @param hcType String the hcType
     * @return List<Demographic>
     */
    public List<Demographic> searchByHealthCard(String hin, String hcType);

    /**
     * Search By Health Card.
     *
     * @param hin String the hin
     * @return List<Demographic>
     */
    public List<Demographic> searchByHealthCard(String hin);

    public Demographic getDemographicByNamePhoneEmail(String firstName, String lastName, String hPhone, String wPhone,
                                                      String email);

    public List<Demographic> getDemographicWithLastFirstDOB(String lastname, String firstname, String year_of_birth,
                                                            String month_of_birth, String date_of_birth);

    public List<Demographic> getDemographicWithLastFirstDOBExact(String lastname, String firstname,
                                                                 String year_of_birth, String month_of_birth, String date_of_birth);

    /**
     * Checks whether a demographic record exists with the given first and last name.
     *
     * @param firstName String the patient's first name (exact match)
     * @param lastName String the patient's last name (exact match)
     * @return boolean true if at least one matching record exists, false otherwise
     */
    public boolean existsByFirstAndLastName(String firstName, String lastName);

    /**
     * Get Demographics By Health Num.
     *
     * @param hin String the hin
     * @return List<Demographic>
     */
    public List<Demographic> getDemographicsByHealthNum(String hin);

    /**
     * Get Active Demographic Ids.
     * @return List<Integer>
     */
    public List<Integer> getActiveDemographicIds();

    /**
     * Get Demographic Ids.
     * @return List<Integer>
     */
    public List<Integer> getDemographicIds();

    /**
     * Get Demographic With Greater Than Year Of Birth.
     *
     * @param yearOfBirth int the yearOfBirth
     * @return List<Demographic>
     */
    public List<Demographic> getDemographicWithGreaterThanYearOfBirth(int yearOfBirth);

    /**
     * Search_catchment.
     *
     * @param rosterStatus String the rosterStatus
     * @param offset int the offset
     * @param limit int the limit
     * @return List<Demographic>
     */
    public List<Demographic> search_catchment(String rosterStatus, int offset, int limit);

    /**
     * Find By Field.
     *
     * @param fieldName String the fieldName
     * @param fieldValue Object the fieldValue
     * @param orderBy String the orderBy
     * @param offset int the offset
     * @return List<Demographic>
     */
    public List<Demographic> findByField(String fieldName, Object fieldValue, String orderBy, int offset);

    // public List<Demographic> findByCriterion(DemographicCriterion c);

    /**
     * Find Demographics For Flu Report.
     *
     * @param providerNo String the providerNo
     * @return List<Object[]>
     */
    public List<Object[]> findDemographicsForFluReport(String providerNo);

    /**
     * Get Active Demographic Ids Older Than.
     *
     * @param age int the age
     * @return List<Integer>
     */
    public List<Integer> getActiveDemographicIdsOlderThan(int age);

    /**
     * Set Application Event Publisher.
     *
     * @param publisher ApplicationEventPublisher the publisher
     */
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher);

    /**
     * Get Demographic Ids Added Since.
     *
     * @param value Date the value
     * @return List<Integer>
     */
    public List<Integer> getDemographicIdsAddedSince(Date value);

    /**
     * Get Demographic By Roster Status.
     *
     * @param rosterStatus String the rosterStatus
     * @param patientStatus String the patientStatus
     * @return List<Demographic>
     */
    public List<Demographic> getDemographicByRosterStatus(String rosterStatus, String patientStatus);

    /**
     * Search Patient Count.
     *
     * @param loggedInInfo LoggedInInfo the loggedInInfo
     * @param searchRequest DemographicSearchRequest the searchRequest
     * @return Integer
     */
    public Integer searchPatientCount(LoggedInInfo loggedInInfo, DemographicSearchRequest searchRequest);

    public List<DemographicSearchResult> searchPatients(LoggedInInfo loggedInInfo,
                                                        DemographicSearchRequest searchRequest, int startIndex, int itemsToReturn);


    /**
     * Get Missing Ext Key.
     *
     * @param keyName String the keyName
     * @return List<Integer>
     */
    public List<Integer> getMissingExtKey(String keyName);


    /**
     * Get Active Demographic After.
     *
     * @param afterDatetimeExclusive Date the afterDatetimeExclusive
     * @return List<Demographic>
     */
    public List<Demographic> getActiveDemographicAfter(Date afterDatetimeExclusive);

    /**
     * Find By Last Name And Dob.
     *
     * @param lastName String the lastName
     * @param dateOfBirth Calendar the dateOfBirth
     * @return List<Demographic>
     */
    public List<Demographic> findByLastNameAndDob(String lastName, Calendar dateOfBirth);

    /**
     * Find By First And Last Name.
     *
     * @param name String the name
     * @param start String the start
     * @param end String the end
     * @return List<Demographic>
     */
    public List<Demographic> findByFirstAndLastName(String name, String start, String end);

    /**
     * Find By Dob.
     *
     * @param dateOfBirth Calendar the dateOfBirth
     * @param start String the start
     * @param numToReturn int the numToReturn
     * @return List<Demographic>
     */
    public List<Demographic> findByDob(Calendar dateOfBirth, String start, int numToReturn);

    /**
     * Find By Phone.
     *
     * @param phone String the phone
     * @param start String the start
     * @param numToReturn int the numToReturn
     * @return List<Demographic>
     */
    public List<Demographic> findByPhone(String phone, String start, int numToReturn);

    /**
     * Find By Hin.
     *
     * @param hin String the hin
     * @param start String the start
     * @param numToReturn int the numToReturn
     * @return List<Demographic>
     */
    public List<Demographic> findByHin(String hin, String start, int numToReturn);
}
