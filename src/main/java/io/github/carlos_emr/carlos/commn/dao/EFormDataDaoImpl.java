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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.persistence.Query;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;

@Repository
public class EFormDataDaoImpl extends AbstractDaoImpl<EFormData> implements EFormDataDao {
    
    private static final Logger logger = MiscUtils.getLogger();

    public EFormDataDaoImpl() {
        super(EFormData.class);
    }


    @Override
    public List<EFormData> findByDemographicId(Integer demographicId) {
        Query query = entityManager
                .createQuery("select x from " + modelClass.getSimpleName() + " x where x.demographicId=?1");
        query.setParameter(1, demographicId);

        @SuppressWarnings("unchecked")
        List<EFormData> results = query.getResultList();

        return (results);
    }

    @Override
    public List<EFormData> findByDemographicIdSinceLastDate(Integer demographicId, Date lastDate) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(lastDate);

        Query query = entityManager.createQuery("select x from " + modelClass.getSimpleName()
                + " x where x.demographicId=?1 and x.formDate > ?2 or (x.formDate= ?3 and x.formTime >= ?4)");
        query.setParameter(1, demographicId);
        query.setParameter(2, lastDate);
        query.setParameter(3, lastDate);
        query.setParameter(4, lastDate);

        @SuppressWarnings("unchecked")
        List<EFormData> results = query.getResultList();

        return (results);
    }

    // for integrator
    @Override
    public List<Integer> findDemographicIdSinceLastDate(Date lastDate) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(lastDate);

        Query query = entityManager.createQuery("select x.demographicId from " + modelClass.getSimpleName()
                + " x where x.formDate > ?1 or (x.formDate= ?2 and x.formTime >= ?3)");
        query.setParameter(1, lastDate);
        query.setParameter(2, lastDate);
        query.setParameter(3, lastDate);

        @SuppressWarnings("unchecked")
        List<Integer> results = query.getResultList();

        return (results);
    }

    @Override
    public EFormData findByFormDataId(Integer formDataId) {
        Query query = entityManager.createQuery("select x from " + modelClass.getSimpleName() + " x where x.id=?1");
        query.setParameter(1, formDataId);

        return this.getSingleResultOrNull(query);
    }

    @Override
    public List<EFormData> findByDemographicIdCurrent(Integer demographicId, Boolean current) {
        return findByDemographicIdCurrent(demographicId, current, 0, EFormDataDao.MAX_LIST_RETURN_SIZE);
    }

    /**
     * @param demographicId can not be null
     * @param current       can be null for both
     * @return list of EFormData
     */
    @Override
    public List<EFormData> findByDemographicIdCurrent(Integer demographicId, Boolean current, int startIndex,
                                                      int numToReturn) {
        return findByDemographicIdCurrent(demographicId, current, startIndex, numToReturn, null);
    }

    @Override
    public List<EFormData> findByDemographicIdCurrentAttachedToConsult(String consultationId) {
        String sql = "SELECT * FROM eform_data e JOIN consultdocs cd ON e.fdid = cd.document_no WHERE e.patient_independent = false AND cd.requestId = ?1 "
                + "AND cd.docType = 'E' AND cd.deleted IS NULL " +
                "ORDER BY e.form_date DESC, e.form_time DESC";
        Query query = entityManager.createNativeQuery(sql, modelClass);
        query.setParameter(1, consultationId);

        return query.getResultList();
    }

    @Override
    public List<EFormData> findByDemographicIdCurrentAttachedToEForm(String fdid) {
        if (StringUtils.isEmpty(fdid)) {
            return new ArrayList<EFormData>();
        }

        String sql = "SELECT * FROM eform_data e where e.patient_independent = false " +
                "AND e.fdid IN (SELECT cd.document_no FROM EFormDocs cd WHERE cd.fdid = ?1 " +
                "AND cd.docType = 'E' AND cd.deleted IS NULL) " +
                "ORDER BY e.form_date DESC, e.form_time DESC";
        Query query = entityManager.createNativeQuery(sql, modelClass);
        query.setParameter(1, fdid);

        return query.getResultList();
    }

    @Override
    public List<EFormData> findByDemographicIdCurrent(Integer demographicId, Boolean current, int startIndex,
                                                      int numToReturn, String sortBy) {
        StringBuilder sb = new StringBuilder();
        sb.append("select x from ");
        sb.append(modelClass.getSimpleName());
        sb.append(" x where x.demographicId=?1 and x.patientIndependent=false");

        if (current != null) {
            sb.append(" and x.current=?2");
        }

        sb.append(" order by ");

        if (sortBy != null) {
            if (SORT_NAME.equals(sortBy))
                sb.append(" x.formName");
            else if (SORT_SUBJECT.equals(sortBy))
                sb.append(" x.subject");
            else
                sb.append(" x.formDate DESC, x.formTime DESC");
        }

        if (sortBy == null) {
            sb.append(" x.formDate DESC, x.formTime DESC");
        }

        String sqlCommand = sb.toString();

        logger.debug("SqlCommand=" + sqlCommand);
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setFirstResult(startIndex);
        query.setMaxResults(numToReturn);

        if (current != null) {
            query.setParameter(2, current);
        }

        @SuppressWarnings("unchecked")
        List<EFormData> results = query.getResultList();

        return (results);
    }

    /**
     * @param demographicId can not be null
     * @param current       can be null for both
     * @return list of maps
     */
    @Override
    public List<Map<String, Object>> findByDemographicIdCurrentNoData(Integer demographicId, Boolean current) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "select new map(x.id as id, x.formId as formId, x.formName as formName, x.subject as subject, x.demographicId as demographicId, x.current as current, x.formDate as formDate, x.formTime as formTime, x.providerNo as providerNo, x.patientIndependent as patientIndependent, x.roleType as roleType) from ");
        sb.append(modelClass.getSimpleName());
        sb.append(" x where x.demographicId=?1");
        sb.append(" and x.patientIndependent=false");

        if (current != null) {
            sb.append(" and x.current=?2");
        }

        String sqlCommand = sb.toString();
        logger.debug("SqlCommand=" + sqlCommand);

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);

        if (current != null) {
            query.setParameter(2, current);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = query.getResultList();

        return (results);
    }

    @Override
    public List<EFormData> findPatientIndependent(Boolean current) {
        StringBuilder sb = new StringBuilder();
        sb.append("select x from ");
        sb.append(modelClass.getSimpleName());
        sb.append(" x where x.patientIndependent=true");

        if (current != null) {
            sb.append(" and x.current=?1");
        }

        String sqlCommand = sb.toString();
        logger.debug("SqlCommand=" + sqlCommand);

        Query query = entityManager.createQuery(sqlCommand);

        if (current != null) {
            query.setParameter(1, current);
        }

        @SuppressWarnings("unchecked")
        List<EFormData> results = query.getResultList();

        return results;
    }

    @Override
    public List<EFormData> findByFormId(Integer formId) {

        Query query = entityManager.createQuery(
                "select x from " + modelClass.getSimpleName() + " x where x.formId = ?1 and x.current = 1");
        query.setParameter(1, formId);

        @SuppressWarnings("unchecked")
        List<EFormData> results = query.getResultList();

        return results;
    }

    @Override
    public List<Integer> findDemographicNosByFormId(Integer formId) {

        Query query = entityManager.createQuery("select x.demographicId from " + modelClass.getSimpleName()
                + " x where x.formId = ?1 and x.current = 1");
        query.setParameter(1, formId);

        @SuppressWarnings("unchecked")
        List<Integer> results = query.getResultList();

        return results;
    }

    @Override
    public List<Integer> findAllFdidByFormId(Integer formId) {

        Query query = entityManager
                .createQuery("select distinct x.id from " + modelClass.getSimpleName() + " x where x.formId = ?1");
        query.setParameter(1, formId);

        @SuppressWarnings("unchecked")
        List<Integer> results = query.getResultList();

        return results;
    }

    // for EFormReportTool
    @Override
    public List<Object[]> findMetaFieldsByFormId(Integer formId) {

        Query query = entityManager
                .createQuery("select distinct x.id, x.demographicId,x.formDate, x.formTime, x.providerNo  from "
                        + modelClass.getSimpleName() + " x where x.formId = ?1");
        query.setParameter(1, formId);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        return results;
    }

    @Override
    public List<Integer> findAllCurrentFdidByFormId(Integer formId) {

        Query query = entityManager.createQuery(
                "select distinct x.id from " + modelClass.getSimpleName() + " x where x.formId = ?1 and x.current = 1");
        query.setParameter(1, formId);

        @SuppressWarnings("unchecked")
        List<Integer> results = query.getResultList();

        return results;
    }

    @Override
    public List<EFormData> findByFormIdProviderNo(List<String> providerNo, Integer formId) {

        Query query = entityManager.createQuery("select x from " + modelClass.getSimpleName()
                + " x where x.formId = ?1 and x.providerNo in (?2) and x.current = 1");
        query.setParameter(1, formId);
        query.setParameter(2, providerNo);

        @SuppressWarnings("unchecked")
        List<EFormData> results = query.getResultList();

        return results;
    }

    /**
     * Finds form data for the specified demographic record and form name
     *
     * @param demographicNo Demographic number to find the form data for
     * @param formName      Form name to find the data for
     * @return Returns all active matching form data, ordered by creation date and
     * time
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<EFormData> findByDemographicIdAndFormName(Integer demographicNo, String formName) {
        String queryString = "FROM EFormData e WHERE e.demographicId = ?1 AND e.formName LIKE ?2 and status = '1' ORDER BY e.formDate, e.formTime DESC";
        Query query = entityManager.createQuery(queryString);
        query.setParameter(1, demographicNo);
        query.setParameter(2, formName);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<EFormData> findByDemographicIdAndFormId(Integer demographicNo, Integer fid) {
        String queryString = "FROM EFormData e WHERE e.demographicId = ?1 AND e.formId = ?2 and status = '1' ORDER BY e.formDate DESC, e.formTime DESC";
        Query query = entityManager.createQuery(queryString);
        query.setParameter(1, demographicNo);
        query.setParameter(2, fid);
        return query.getResultList();
    }

    @Override
    public List<EFormData> findByFidsAndDates(TreeSet<Integer> fids, Date dateStart, Date dateEnd) {
        if (fids == null || fids.isEmpty())
            return null;

        Query query = entityManager.createQuery("select x from " + modelClass.getSimpleName()
                + " x where x.current=1 and x.formId in (?1) and x.formDate>=?2 and x.formDate<?3");
        query.setParameter(1, fids);
        query.setParameter(2, dateStart);
        query.setParameter(3, dateEnd);

        @SuppressWarnings("unchecked")
        List<EFormData> results = query.getResultList();

        return (results);
    }

    @Override
    public List<EFormData> findByFdids(List<Integer> ids) {
        if (ids.size() == 0)
            return new ArrayList<EFormData>();

        Query query = entityManager
                .createQuery("select x from " + modelClass.getSimpleName() + " x where x.id in (?1)");
        query.setParameter(1, ids);

        @SuppressWarnings("unchecked")
        List<EFormData> results = query.getResultList();

        return results;
    }

    @Override
    public boolean isLatestShowLatestFormOnlyPatientForm(Integer fdid) {
        // 1) this is a ShowLatestFormOnly eform AND
        // 2.1) the patient has only 1 eform of the same fid OR
        // 2.2) this is the patient's latest eform of the same fid

        EFormData eformData = this.find(fdid);
        if (eformData == null)
            return false;
        if (!eformData.isShowLatestFormOnly())
            return false;

        List<EFormData> sameEformList = this.getFormsSameFidSamePatient(fdid);
        if (sameEformList.size() == 1)
            return true;

        for (EFormData otherEform : sameEformList) {
            if (otherEform.getId().equals(fdid))
                continue; // current eform

            Date eformDataDate = eformData.getFormDate();
            Date eformDataTime = eformData.getFormTime();
            Date otherEformDate = otherEform.getFormDate();
            Date otherEformTime = otherEform.getFormTime();

            if (eformDataDate != null && otherEformDate != null) {
                if (otherEformDate.after(eformDataDate))
                    return false;

                if (eformDataTime != null && otherEformTime != null) {
                    if (eformDataDate.equals(otherEformDate) && otherEformTime.after(eformDataTime))
                        return false;
                }
            }
            if (eformDataDate.equals(otherEformDate) && eformDataTime.equals(otherEformTime)
                    && otherEform.getId() > fdid)
                return false;
        }
        return true;
    }

    @Override
    public List<EFormData> getFormsSameFidSamePatient(Integer fdid) {
        EFormData eformData = this.find(fdid);
        if (eformData == null)
            return new ArrayList<EFormData>(); // empty list

        List<EFormData> efmDataList = this.findByDemographicIdCurrent(eformData.getDemographicId(), true);

        for (int i = 0; i < efmDataList.size(); i++) {
            if (!eformData.getFormId().equals(efmDataList.get(i).getFormId())) {
                efmDataList.remove(i);
                i--;
            }
        }
        return efmDataList;
    }

    // for integrator
    @Override
    public List<Integer> findemographicIdSinceLastDate(Date lastDate) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(lastDate);

        Query query = entityManager.createQuery("select x.demographicId from " + modelClass.getSimpleName()
                + " x where x.formDate > ?1 or (x.formDate= ?2 and x.formTime >= ?3)");
        query.setParameter(1, lastDate);
        query.setParameter(2, lastDate);
        query.setParameter(3, lastDate);

        @SuppressWarnings("unchecked")
        List<Integer> results = query.getResultList();

        return (results);
    }

    @Override
    public List<EFormData> findInGroups(Boolean status, int demographicNo, String groupName, String sortBy, int offset,
                                        int numToReturn, List<String> eformPerms) {
        StringBuilder sb = new StringBuilder(
                "SELECT e FROM EFormData e, EFormGroup g WHERE e.demographicId = ?1 AND e.patientIndependent = false AND e.formId = g.formId AND g.groupName = ?2");

        if (status != null) {
            sb.append(" AND e.current = ?3");
        }
        
        // get list of _eform.???? permissions the caller has
        if (eformPerms != null && eformPerms.size() > 0) {
            sb.append(" AND (e.roleType in (?4) OR e.roleType IS NULL OR e.roleType = '' OR e.roleType = 'null')");
        }

        sb.append(" ORDER BY ");

        if (sortBy != null) {
            if (SORT_NAME.equals(sortBy))
                sb.append(" e.formName");
            else if (SORT_SUBJECT.equals(sortBy))
                sb.append(" e.subject");
            else
                sb.append(" e.formDate DESC, e.formTime DESC");
        } else {
            sb.append(" e.formDate DESC, e.formTime DESC");
        }

        Query query = entityManager.createQuery(sb.toString());
        query.setParameter(1, demographicNo);
        query.setParameter(2, groupName);
        if (status != null) {
            query.setParameter(3, status);
        }
        if (eformPerms != null && eformPerms.size() > 0) {
            query.setParameter(4, eformPerms);
        }
        query.setFirstResult(offset);
        this.setLimit(query, numToReturn);

        return query.getResultList();
    }

    @Override
    public Integer getLatestFdid(Integer fid, Integer demographicNo) {
        Query query = entityManager.createQuery("select max(x.id) from " + modelClass.getSimpleName()
                + " x where x.current=1 and x.formId = ?1 and x.demographicId = ?2");
        query.setParameter(1, fid);
        query.setParameter(2, demographicNo);

        List<Integer> results = query.getResultList();
        if (results.size() == 1) {
            if (results.get(0) != null) {
                return (results.get(0).intValue());
            }
            return null;
        } else if (results.size() == 0)
            return (null);

        return null;
    }

    /**
     * This method war written for BORN Kid eConnect job to figure out which eforms
     * don't have an eform_value present
     *
     * @param fid
     * @param varName
     * @return
     */
    @Override
    public List<Integer> getDemographicNosMissingVarName(int fid, String varName) {

        Query query = entityManager.createNativeQuery(
                "select distinct d.demographic_no from eform e,eform_data d,eform_values v where e.fid = ?1 and e.fid = d.fid and d.fdid = v.fdid and d.fdid not in (select distinct d.fdid from eform e,eform_data d,eform_values v where e.fid = d.fid and d.fdid = v.fdid and e.fid=?2 and v.var_name=?3)");
        query.setParameter(1, fid);
        query.setParameter(2, fid);
        query.setParameter(3, varName);

        List<Integer> results = query.getResultList();

        return results;
    }

    @Override
    public List<String> getProvidersForEforms(Collection<Integer> fdidList) {

        Query query = entityManager.createQuery(
                "select distinct x.providerNo from " + modelClass.getSimpleName() + " x where x.id in (?1)");
        query.setParameter(1, fdidList);
        
        List<String> results = query.getResultList();

        return results;
    }

    @Override
    public Date getLatestFormDateAndTimeForEforms(Collection<Integer> fdidList) {

        Query query = entityManager.createQuery("select distinct x.formDate,x.formTime from "
                + modelClass.getSimpleName() + " x where x.id in (?1) order by x.formDate DESC, x.formTime DESC");
        query.setParameter(1, fdidList);

        List<Object[]> results = query.getResultList();

        if (!results.isEmpty()) {
            Date date = (Date) results.get(0)[0];
            Date time = (Date) results.get(0)[1];

            Calendar cal = Calendar.getInstance();
            cal.setTime(time);
            int timeComponentInMillis = ((cal.get(Calendar.HOUR_OF_DAY) * 60 * 60) + (cal.get(Calendar.MINUTE) * 60)
                    + cal.get(Calendar.SECOND)) * 1000;


            Date d = new Date(date.getTime() + timeComponentInMillis);
            return d;
        }
        return null;
    }
}
