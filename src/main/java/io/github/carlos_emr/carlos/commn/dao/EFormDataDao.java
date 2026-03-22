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

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import io.github.carlos_emr.carlos.commn.model.EFormData;

/**
 * DAO interface for electronic form operations.
 *
 * @since 2001
 */

public interface EFormDataDao extends AbstractDao<EFormData> {

    public static final String SORT_NAME = "form_name";
    public static final String SORT_SUBJECT = "subject";

    /**
     * Find By Demographic Id.
     *
     * @param demographicId Integer the demographicId
     * @return List<EFormData>
     */
    public List<EFormData> findByDemographicId(Integer demographicId);

    /**
     * Find By Demographic Id Since Last Date.
     *
     * @param demographicId Integer the demographicId
     * @param lastDate Date the lastDate
     * @return List<EFormData>
     */
    public List<EFormData> findByDemographicIdSinceLastDate(Integer demographicId, Date lastDate);

    /**
     * Find Demographic Id Since Last Date.
     *
     * @param lastDate Date the lastDate
     * @return List<Integer>
     */
    public List<Integer> findDemographicIdSinceLastDate(Date lastDate);

    /**
     * Find By Form Data Id.
     *
     * @param formDataId Integer the formDataId
     * @return EFormData
     */
    public EFormData findByFormDataId(Integer formDataId);

    /**
     * Find By Demographic Id Current.
     *
     * @param demographicId Integer the demographicId
     * @param current Boolean the current
     * @return List<EFormData>
     */
    public List<EFormData> findByDemographicIdCurrent(Integer demographicId, Boolean current);

    public List<EFormData> findByDemographicIdCurrent(Integer demographicId, Boolean current, int startIndex,
                                                      int numToReturn);

    /**
     * Find By Demographic Id Current Attached To Consult.
     *
     * @param consultationId String the consultationId
     * @return List<EFormData>
     */
    public List<EFormData> findByDemographicIdCurrentAttachedToConsult(String consultationId);

    /**
     * Find By Demographic Id Current Attached To E Form.
     *
     * @param fdid String the fdid
     * @return List<EFormData>
     */
    public List<EFormData> findByDemographicIdCurrentAttachedToEForm(String fdid);

    public List<EFormData> findByDemographicIdCurrent(Integer demographicId, Boolean current, int startIndex,
                                                      int numToReturn, String sortBy);

    /**
     * Find By Demographic Id Current No Data.
     *
     * @param demographicId Integer the demographicId
     * @param current Boolean the current
     * @return List<Map<String, Object>>
     */
    public List<Map<String, Object>> findByDemographicIdCurrentNoData(Integer demographicId, Boolean current);

    /**
     * Find Patient Independent.
     *
     * @param current Boolean the current
     * @return List<EFormData>
     */
    public List<EFormData> findPatientIndependent(Boolean current);

    /**
     * Find By Form Id.
     *
     * @param formId Integer the formId
     * @return List<EFormData>
     */
    public List<EFormData> findByFormId(Integer formId);

    /**
     * Find Demographic Nos By Form Id.
     *
     * @param formId Integer the formId
     * @return List<Integer>
     */
    public List<Integer> findDemographicNosByFormId(Integer formId);

    /**
     * Find All Fdid By Form Id.
     *
     * @param formId Integer the formId
     * @return List<Integer>
     */
    public List<Integer> findAllFdidByFormId(Integer formId);

    /**
     * Find Meta Fields By Form Id.
     *
     * @param formId Integer the formId
     * @return List<Object[]>
     */
    public List<Object[]> findMetaFieldsByFormId(Integer formId);

    /**
     * Find All Current Fdid By Form Id.
     *
     * @param formId Integer the formId
     * @return List<Integer>
     */
    public List<Integer> findAllCurrentFdidByFormId(Integer formId);

    /**
     * Find By Form Id Provider No.
     *
     * @param providerNo List<String> the providerNo
     * @param formId Integer the formId
     * @return List<EFormData>
     */
    public List<EFormData> findByFormIdProviderNo(List<String> providerNo, Integer formId);

    /**
     * Find By Demographic Id And Form Name.
     *
     * @param demographicNo Integer the demographicNo
     * @param formName String the formName
     * @return List<EFormData>
     */
    public List<EFormData> findByDemographicIdAndFormName(Integer demographicNo, String formName);

    /**
     * Find By Demographic Id And Form Id.
     *
     * @param demographicNo Integer the demographicNo
     * @param fid Integer the fid
     * @return List<EFormData>
     */
    public List<EFormData> findByDemographicIdAndFormId(Integer demographicNo, Integer fid);

    /**
     * Find By Fids And Dates.
     *
     * @param fids TreeSet<Integer> the fids
     * @param dateStart Date the dateStart
     * @param dateEnd Date the dateEnd
     * @return List<EFormData>
     */
    public List<EFormData> findByFidsAndDates(TreeSet<Integer> fids, Date dateStart, Date dateEnd);

    /**
     * Find By Fdids.
     *
     * @param ids List<Integer> the ids
     * @return List<EFormData>
     */
    public List<EFormData> findByFdids(List<Integer> ids);

    /**
     * Is Latest Show Latest Form Only Patient Form.
     *
     * @param fdid Integer the fdid
     * @return boolean
     */
    public boolean isLatestShowLatestFormOnlyPatientForm(Integer fdid);

    /**
     * Get Forms Same Fid Same Patient.
     *
     * @param fdid Integer the fdid
     * @return List<EFormData>
     */
    public List<EFormData> getFormsSameFidSamePatient(Integer fdid);

    /**
     * Findemographic Id Since Last Date.
     *
     * @param lastDate Date the lastDate
     * @return List<Integer>
     */
    public List<Integer> findemographicIdSinceLastDate(Date lastDate);

    public List<EFormData> findInGroups(Boolean status, int demographicNo, String groupName, String sortBy, int offset,
                                        int numToReturn, List<String> eformPerms);

    /**
     * Get Latest Fdid.
     *
     * @param fid Integer the fid
     * @param demographicNo Integer the demographicNo
     * @return Integer
     */
    public Integer getLatestFdid(Integer fid, Integer demographicNo);

    /**
     * Get Demographic Nos Missing Var Name.
     *
     * @param fid int the fid
     * @param varName String the varName
     * @return List<Integer>
     */
    public List<Integer> getDemographicNosMissingVarName(int fid, String varName);

    /**
     * Get Providers For Eforms.
     *
     * @param fdidList Collection<Integer> the fdidList
     * @return List<String>
     */
    public List<String> getProvidersForEforms(Collection<Integer> fdidList);

    /**
     * Get Latest Form Date And Time For Eforms.
     *
     * @param fdidList Collection<Integer> the fdidList
     * @return Date
     */
    public Date getLatestFormDateAndTimeForEforms(Collection<Integer> fdidList);

}
