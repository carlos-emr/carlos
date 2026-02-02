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

package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import io.github.carlos_emr.carlos.commn.model.EFormData;

public interface EFormDataDao extends AbstractDao<EFormData> {

    public static final String SORT_NAME = "form_name";
    public static final String SORT_SUBJECT = "subject";

    public List<EFormData> findByDemographicId(Integer demographicId);

    public List<EFormData> findByDemographicIdSinceLastDate(Integer demographicId, Date lastDate);

    public List<Integer> findDemographicIdSinceLastDate(Date lastDate);

    public EFormData findByFormDataId(Integer formDataId);

    public List<EFormData> findByDemographicIdCurrent(Integer demographicId, Boolean current);

    public List<EFormData> findByDemographicIdCurrent(Integer demographicId, Boolean current, int startIndex,
                                                      int numToReturn);

    public List<EFormData> findByDemographicIdCurrentAttachedToConsult(String consultationId);

    public List<EFormData> findByDemographicIdCurrentAttachedToEForm(String fdid);

    public List<EFormData> findByDemographicIdCurrent(Integer demographicId, Boolean current, int startIndex,
                                                      int numToReturn, String sortBy);

    public List<Map<String, Object>> findByDemographicIdCurrentNoData(Integer demographicId, Boolean current);

    public List<EFormData> findPatientIndependent(Boolean current);

    public List<EFormData> findByFormId(Integer formId);

    public List<Integer> findDemographicNosByFormId(Integer formId);

    public List<Integer> findAllFdidByFormId(Integer formId);

    public List<Object[]> findMetaFieldsByFormId(Integer formId);

    public List<Integer> findAllCurrentFdidByFormId(Integer formId);

    public List<EFormData> findByFormIdProviderNo(List<String> providerNo, Integer formId);

    public List<EFormData> findByDemographicIdAndFormName(Integer demographicNo, String formName);

    public List<EFormData> findByDemographicIdAndFormId(Integer demographicNo, Integer fid);

    public List<EFormData> findByFidsAndDates(TreeSet<Integer> fids, Date dateStart, Date dateEnd);

    public List<EFormData> findByFdids(List<Integer> ids);

    public boolean isLatestShowLatestFormOnlyPatientForm(Integer fdid);

    public List<EFormData> getFormsSameFidSamePatient(Integer fdid);

    public List<Integer> findemographicIdSinceLastDate(Date lastDate);

    public List<EFormData> findInGroups(Boolean status, int demographicNo, String groupName, String sortBy, int offset,
                                        int numToReturn, List<String> eformPerms);

    public Integer getLatestFdid(Integer fid, Integer demographicNo);

    public List<Integer> getDemographicNosMissingVarName(int fid, String varName);

    public List<String> getProvidersForEforms(Collection<Integer> fdidList);

    public Date getLatestFormDateAndTimeForEforms(Collection<Integer> fdidList);

}
