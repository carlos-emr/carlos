/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 *
 * Modifications made by Magenta Health in 2024.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Objects;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.github.carlos_emr.carlos.utils.Utility;
import org.apache.commons.lang3.StringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.apache.logging.log4j.Logger;
import org.hibernate.query.NativeQuery;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientListsReportFormBean;
import io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientSearchFormBean;
import io.github.carlos_emr.carlos.commn.DemographicSearchResultTransformer;
import io.github.carlos_emr.carlos.commn.Gender;
import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.demographic.dto.DemographicHeaderDTO;
import io.github.carlos_emr.carlos.demographic.dto.DemographicListItemDTO;
import io.github.carlos_emr.carlos.event.DemographicCreateEvent;
import io.github.carlos_emr.carlos.event.DemographicUpdateEvent;
import io.github.carlos_emr.carlos.integration.hl7.generators.HL7A04Generator;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest.SEARCHMODE;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest.SORTMODE;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import io.github.carlos_emr.carlos.dao.AbstractJpaDao;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.JpqlQueryHelper;
import io.github.carlos_emr.carlos.utility.LogSafe;

/**
 *
 */
@Transactional
public class DemographicDaoImpl extends AbstractJpaDao implements ApplicationEventPublisherAware, DemographicDao {

    private static final int MAX_SELECT_SIZE = 500;

    /** Parameter keys whose values contain PHI and must not appear in logs. */
    private static final Set<String> PHI_PARAM_KEYS = Set.of(
        "fnLike", "lnLike", "fnSoundex", "lnSoundex", "hin", "ver", "dob", "yob", "mob", "dayob"
    );

    static Logger log = MiscUtils.getLogger();

    private ApplicationEventPublisher publisher;


    /**
     * Finds merged demographic IDs for the specified demographic.
     *
     * @param demographicNo Demographic ID to find merged records for
     * @return Returns the list of merged (child ids) or empty list if the record is
     * not merged to any other record
     */
    @NativeSql("demographic_merged")
    @Override
    public List<Integer> getMergedDemographics(Integer demographicNo) {
        EntityManager session = entityManager();
        Query sqlQuery = session.createNativeQuery(
            "select demographic_no from demographic_merged where merged_to = :parentId and deleted = 0");
        sqlQuery.setParameter("parentId", demographicNo);
        // Native queries return driver-dependent numeric types (Integer / Long / BigInteger).
        // Normalise via Number.intValue() so callers reliably receive List<Integer>.
        @SuppressWarnings("unchecked")
        List<Number> rawResults = sqlQuery.getResultList();
        List<Integer> results = new ArrayList<>(rawResults.size());
        for (Number row : rawResults) {
            results.add(row.intValue());
        }
        return results;
    }

    @Override
    public Demographic getDemographic(String demographic_no) {
        if (demographic_no == null || demographic_no.length() == 0) {
            return null;
        }
        int dNo = 0;
        try {
            dNo = Integer.parseInt(demographic_no);
        } catch (NumberFormatException e) {
            return null;
        }

        return entityManager().find(Demographic.class, dNo);
    }

    @Override
    public List getDemographics() {
        log.error(
            "No one should be calling this method, this is a good way to run out of memory and crash a server... this is too large of a result set, it should be pagenated.",
            new IllegalArgumentException("The entire demographic table is too big to allow a full select."));
        return JpqlQueryHelper.find(entityManager(), "from Demographic d order by d.lastName");
    }

    @Override
    public Long getActiveDemographicCount() {
        List<?> res = JpqlQueryHelper.find(entityManager(),
            "SELECT COUNT(*) FROM Demographic d WHERE d.patientStatus = 'AC'");
        for (Object r : res) {
            return (Long) r;
        }
        return 0L;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Demographic> getActiveDemographics(final int offset, final int limit) {
        Query query = entityManager().createQuery("FROM Demographic d WHERE d.patientStatus = 'AC'");
        if (offset > 0) {
            query.setFirstResult(offset);
        }
        int aLimit = limit;
        if (aLimit <= 0) {
            aLimit = MAX_SELECT_SIZE;
        }
        if (aLimit > MAX_SELECT_SIZE) {
            throw new MaxSelectLimitExceededException(MAX_SELECT_SIZE, aLimit);
        }
        query.setMaxResults(aLimit);

        return query.getResultList();
    }

    @Override
    public Demographic getDemographicById(Integer demographic_id) {
        String q = "FROM Demographic d WHERE d.demographicNo = ?1";
        List rs = JpqlQueryHelper.find(entityManager(), q, demographic_id);

        if (rs.size() == 0)
            return null;
        else
            return (Demographic) rs.get(0);
    }

    @Override
    public List<Demographic> getDemographicByProvider(String providerNo) {
        return getDemographicByProvider(providerNo, true);
    }

    @Override
    public List<Demographic> getDemographicByProvider(String providerNo, boolean onlyActive) {
        String q = "From Demographic d where d.providerNo = ?1 ";
        if (onlyActive) {
            q = "From Demographic d where d.providerNo = ?1 and d.patientStatus = 'AC' ";
        }
        List<Demographic> rs = (List<Demographic>) JpqlQueryHelper.find(entityManager(), q, providerNo);
        return rs;
    }

    @Override
    public List<Integer> getDemographicNosByProvider(String providerNo, boolean onlyActive) {
        String q = "Select d.demographicNo From Demographic d where d.providerNo = ?1 ";
        if (onlyActive) {
            q = "Select d.demographicNo From Demographic d where d.providerNo = ?1 and d.patientStatus = 'AC' ";
        }
        List<Integer> rs = (List<Integer>) JpqlQueryHelper.find(entityManager(), q, providerNo);
        return rs;
    }


    /*
     * get demographics according to their program, admit time, discharge time,
     * ordered by lastname and first name
     */
    @Override
    public List getActiveDemographicByProgram(int programId, Date dt, Date defdt) {
        // get duplicated clients from this sql
        String q = "Select d From Demographic d, Admission a Where (d.patientStatus=?1 or d.patientStatus='' or d.patientStatus=null) and d.demographicNo=a.clientId and a.programId=?2 and a.admissionDate<=?3 and (a.dischargeDate>=?4 or (a.dischargeDate is null) or a.dischargeDate=?5) order by d.lastName,d.firstName";

        String status = "AC"; // only show active clients
        List rs = JpqlQueryHelper.find(entityManager(), q, status, Integer.valueOf(programId), dt, dt, defdt);

        List clients = new ArrayList<Demographic>();
        Integer clientNo = 0;
        Iterator it = rs.iterator();
        while (it.hasNext()) {
            Demographic demographic = (Demographic) it.next();

            // no dumplicated clients.
            if (demographic.getDemographicNo() == clientNo)
                continue;

            clientNo = demographic.getDemographicNo();

            clients.add(demographic);
        }
        // return rs;
        return clients;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> getActiveDemosByHealthCardNo(String hcn, String hcnType) {
        String hql = "FROM Demographic d WHERE d.hin = :hin AND d.hcType = :hcType AND d.patientStatus = 'AC'";
        EntityManager session = entityManager();
        return session.createQuery(hql, Demographic.class)
            .setParameter("hin", hcn)
            .setParameter("hcType", hcnType)
            .getResultList();
    }

    @Override
    public Set getArchiveDemographicByProgramOptimized(int programId, Date dt, Date defdt) {
        Set<Demographic> archivedClients = new java.util.LinkedHashSet<Demographic>();

        String sqlQuery = "select distinct d.demographic_no,d.first_name,d.last_name,"
            + "(select count(*) from admission a2 where a2.client_id=d.demographic_no and a2.admission_status='current' and a2.program_id=:programId and a2.admission_date<=:admissionDate)"
            + " as is_active from admission a,demographic d where a.client_id=d.demographic_no"
            + " and (d.patient_status='AC' or d.patient_status='' or d.patient_status is null)"
            + " and a.program_id=:programId"
            + " and (d.anonymous is null or d.anonymous != 'one-time-anonymous') ORDER BY d.last_name,d.first_name";
        EntityManager session = entityManager();

        NativeQuery q = session.createNativeQuery(sqlQuery).unwrap(NativeQuery.class);
        q.setParameter("programId", programId);
        q.setParameter("admissionDate", dt);
        q.addScalar("d.demographic_no");
        q.addScalar("d.first_name");
        q.addScalar("d.last_name");
        q.addScalar("is_active");
        List results = q.getResultList();

        Iterator iter = results.iterator();
        while (iter.hasNext()) {
            Object[] result = (Object[]) iter.next();
            if (((Number) result[3]).intValue() == 0) {
                Demographic d = new Demographic();
                d.setDemographicNo((Integer) result[0]);
                d.setFirstName((String) result[1]);
                d.setLastName((String) result[2]);
                archivedClients.add(d);
            }
        }

        return archivedClients;

    }

    @Override
    public List getProgramIdByDemoNo(Integer demoNo) {
        String q = "Select a.programId From Admission a Where a.clientId=?1 and a.admissionDate<=?2 and (a.dischargeDate>=?3 or (a.dischargeDate is null) or a.dischargeDate=?4)";

        /* default time is Oscar default null time 0001-01-01. */
        Date defdt = new GregorianCalendar(1, 0, 1).getTime();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        Date dt = cal.getTime();

        List rs = JpqlQueryHelper.find(entityManager(), q, demoNo, dt, dt, defdt);
        return rs;
    }

    @Override
    public void clear() {
        entityManager().clear();

    }

    @Override
    public List getDemoProgram(Integer demoNo) {
        String q = "Select a.programId From Admission a Where a.clientId=?1";
        List rs = JpqlQueryHelper.find(entityManager(), q, demoNo);
        return rs;
    }

    @Override
    public List getDemoProgramCurrent(Integer demoNo) {
        String q = "Select a.programId From Admission a Where a.clientId=?1 and a.admissionStatus='current'";
        List rs = JpqlQueryHelper.find(entityManager(), q, demoNo);
        return rs;
    }

    public List<Integer> getDemographicIdsAdmittedIntoFacility(int facilityId) {
        String sql = String.join(" ",
                "select distinct(admission.client_id) from admission, program, Facility",
                "where admission.program_id = program.id and program.facilityId = :facilityId");
        Query query = entityManager().createNativeQuery(sql);
        query.setParameter("facilityId", facilityId);

        @SuppressWarnings("unchecked")
        List<Object> rows = query.getResultList();
        ArrayList<Integer> results = new ArrayList<Integer>();
        for (Object row : rows) {
            results.add(((Number) row).intValue());
        }
        return results;
    }

    @Override
    public List<Demographic> searchDemographic(String searchStr) {
        // Defensive: an empty/blank/all-comma input produces a length-0 array
        // (String.split strips trailing empties at default limit), so guard
        // every dereference of parts[].
        if (searchStr == null || searchStr.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String hql = "From Demographic d where ";
        String[] parts = searchStr.split(",");
        if (parts.length == 0 || parts[0].trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        boolean hasFirstName = parts.length > 1
            && searchStr.indexOf(",") != -1
            && searchStr.trim().indexOf(",") != (searchStr.trim().length() - 1);

        if (hasFirstName) {
            hql += "d.lastName like :ln and (d.firstName like :fn or d.alias like :fn)";
        } else {
            hql += "d.lastName like :ln";
        }

        EntityManager session = entityManager();
        var q = session.createQuery(hql, Demographic.class);
        q.setParameter("ln", parts[0].trim() + "%");
        if (hasFirstName) {
            q.setParameter("fn", parts[1].trim() + "%");
        }
        return q.getResultList();
    }

    @Override
    public List<Demographic> searchDemographicByNameString(String searchString, int startIndex, int itemsToReturn) {
        String sqlCommand = "select x from Demographic x";
        EntityManager session = entityManager();
        String ln = "";
        String fn = "";
        String where = "";
            if (searchString != null && searchString.length() > 0) {
                String[] sh = searchString.split(",");
                if (sh.length > 1) {
                    if (sh[0] != null && sh[0].trim().length() > 0) {
                        where = " x.lastName like :ln ";
                        ln = sh[0].trim();
                    }
                    if (sh[1] != null && sh[1].trim().length() > 0) {
                        if (where.length() > 0)
                            where = where + " and ";
                        where = where + "( x.firstName like :fn or x.alias like :fn ) ";
                        fn = sh[1].trim();
                    }
                } else {
                    if (sh[0] != null && sh[0].trim().length() > 0) {
                        where = " x.lastName like :ln ";
                        ln = sh[0].trim();
                    }
                }
                if (where.length() > 0)
                    sqlCommand = sqlCommand + " where " + where;
            }
            var q = session.createQuery(sqlCommand, Demographic.class);
            if (ln.length() > 0)
                q.setParameter("ln", ln + "%");
            if (fn.length() > 0)
                q.setParameter("fn", fn + "%");
            q.setFirstResult(startIndex);
            q.setMaxResults(itemsToReturn);
            return q.getResultList();
    }

    private static final String PROGRAM_DOMAIN_RESTRICTION = "select distinct a.clientId from ProgramProvider pp,Admission a WHERE pp.programId=a.programId AND pp.providerNo=:providerNo";

    @Override
    public List<Demographic> searchDemographicByName(String searchStr, int limit, int offset, String providerNo,
                                                     boolean outOfDomain) {
        return searchDemographicByNameAndStatus(searchStr, null, limit, offset, null, providerNo, outOfDomain, false);
    }

    @Override
    public List<Demographic> searchDemographicByNameAndNotStatus(String searchStr, List<String> statuses, int limit,
                                                                 int offset, String providerNo, boolean outOfDomain) {
        return searchDemographicByNameAndStatus(searchStr, statuses, limit, offset, null, providerNo, outOfDomain,
            true);
    }

    @Override
    public List<Demographic> searchDemographicByNameAndStatus(String searchStr, List<String> statuses, int limit,
                                                              int offset, String providerNo, boolean outOfDomain) {
        return searchDemographicByNameAndStatus(searchStr, statuses, limit, offset, null, providerNo, outOfDomain,
            false);
    }

    @Override
    public List<Demographic> searchDemographicByName(String searchStr, int limit, int offset, String orderBy,
                                                     String providerNo, boolean outOfDomain) {
        return searchDemographicByNameAndStatus(searchStr, null, limit, offset, orderBy, providerNo, outOfDomain,
            false);
    }

    @Override
    public List<Demographic> searchDemographicByNameAndNotStatus(String searchStr, List<String> statuses, int limit,
                                                                 int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return searchDemographicByNameAndStatus(searchStr, statuses, limit, offset, orderBy, providerNo, outOfDomain,
            true);
    }

    @Override
    public List<Demographic> searchDemographicByNameAndStatus(String searchStr, List<String> statuses, int limit,
                                                              int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return searchDemographicByNameAndStatus(searchStr, statuses, limit, offset, orderBy, providerNo, outOfDomain,
            false);
    }

    @Override
    public List<Demographic> searchDemographicByNameAndStatus(String searchStr, List<String> statuses, int limit,
                                                              int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses) {
        return searchDemographicByNameAndStatus(searchStr, statuses, limit, offset, orderBy, providerNo, outOfDomain,
            ignoreStatuses, true);
    }

    @Override
    public List<Demographic> searchDemographicByNameAndStatus(String searchStr, List<String> statuses, int limit,
                                                              int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses,
                                                              boolean ignoreMerged) {
        List<Demographic> list = new ArrayList<Demographic>();
        String queryString = "From Demographic d where d.lastName like :lastName ";

        String[] name = Objects.requireNonNullElse(searchStr, "").split(",");

        if (name.length == 2) {
            queryString += " and (d.firstName like :firstName or d.alias like :firstName) ";
        }

        if (statuses != null) {
            queryString += " and d.patientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }
        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        EntityManager session = entityManager();
            var q = session.createQuery(queryString, Demographic.class);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("lastName", name[0].trim() + "%");
            if (name.length == 2) {
                q.setParameter("firstName", name[1].trim() + "%");
            }

            if (statuses != null) {
                q.setParameter("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }

            list = q.getResultList();
        return list;
    }

    @Override
    public List<Demographic> searchMergedDemographicByName(String searchStr, int limit, int offset, String providerNo,
                                                           boolean outOfDomain) {
        List<Demographic> list = new ArrayList<Demographic>();
        String queryString = "From Demographic d where d.lastName like :lastName and d.headRecord is not null ";

        String[] name = searchStr.split(",");
        if (name.length == 2) {
            queryString += " and (d.firstName like :firstName or d.alias like :firstName) ";
        }

        EntityManager session = entityManager();
            var q = session.createQuery(queryString, Demographic.class);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("lastName", name[0].trim() + "%");
            if (name.length == 2) {
                q.setParameter("firstName", name[1].trim() + "%");
            }

            list = q.getResultList();
        return list;
    }

    @Override
    public List<Demographic> searchDemographicByDOB(String dobStr, int limit, int offset, String providerNo,
                                                    boolean outOfDomain) {
        return searchDemographicByDOBAndStatus(dobStr, null, limit, offset, null, providerNo, outOfDomain, false);
    }

    @Override
    public List<Demographic> searchDemographicByDOBWithMerged(String dobStr, int limit, int offset, String providerNo,
                                                              boolean outOfDomain) {
        return searchDemographicByDOBAndStatus(dobStr, null, limit, offset, null, providerNo, outOfDomain, false,
            false);
    }

    @Override
    public List<Demographic> getByHinAndGenderAndDobAndLastName(String hin, String gender, String dob,
                                                                String lastName) {

        List<Demographic> list = new ArrayList<Demographic>();
        String queryString = "FROM Demographic d WHERE d.hin like :hin AND d.sex = :gender AND d.yearOfBirth like :yearOfBirth AND d.monthOfBirth like :monthOfBirth AND d.dateOfBirth like :dateOfBirth AND d.lastName = :lastName and d.patientStatus != 'MERGED'";
        String[] params = dob.split("-");
        EntityManager session = entityManager();

            Query q = session.createQuery(queryString);
            q.setParameter("hin", hin.trim());
            q.setParameter("gender", gender.trim());
            q.setParameter("dateOfBirth", params[2].trim() + "%");
            q.setParameter("monthOfBirth", params[1].trim() + "%");
            q.setParameter("yearOfBirth", params[0].trim() + "%");
            q.setParameter("lastName", lastName.toUpperCase());
            list = q.getResultList();

        return list;
    }

    @Override
    public List<Demographic> searchDemographicByDOBAndNotStatus(String dobStr, List<String> statuses, int limit,
                                                                int offset, String providerNo, boolean outOfDomain) {
        return searchDemographicByDOBAndStatus(dobStr, statuses, limit, offset, null, providerNo, outOfDomain, true);
    }

    @Override
    public List<Demographic> searchDemographicByDOBAndStatus(String dobStr, List<String> statuses, int limit,
                                                             int offset, String providerNo, boolean outOfDomain) {
        return searchDemographicByDOBAndStatus(dobStr, statuses, limit, offset, null, providerNo, outOfDomain, false);
    }

    @Override
    public List<Demographic> searchDemographicByDOB(String dobStr, int limit, int offset, String orderBy,
                                                    String providerNo, boolean outOfDomain) {
        return searchDemographicByDOBAndStatus(dobStr, null, limit, offset, orderBy, providerNo, outOfDomain, false);
    }

    @Override
    public List<Demographic> searchDemographicByDOBAndNotStatus(String dobStr, List<String> statuses, int limit,
                                                                int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return searchDemographicByDOBAndStatus(dobStr, statuses, limit, offset, orderBy, providerNo, outOfDomain, true);
    }

    @Override
    public List<Demographic> searchDemographicByDOBAndStatus(String dobStr, List<String> statuses, int limit,
                                                             int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return searchDemographicByDOBAndStatus(dobStr, statuses, limit, offset, orderBy, providerNo, outOfDomain,
            false);
    }

    @Override
    public List<Demographic> searchDemographicByDOBAndStatus(String dobStr, List<String> statuses, int limit,
                                                             int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses) {
        return searchDemographicByDOBAndStatus(dobStr, statuses, limit, offset, orderBy, providerNo, outOfDomain,
            ignoreStatuses, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchDemographicByDOBAndStatus(String dobStr, List<String> statuses, int limit,
                                                             int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses,
                                                             boolean ignoreMerged) {
        List<Demographic> list = new ArrayList<Demographic>();
        String queryString = "From Demographic d where d.yearOfBirth like :yearOfBirth AND d.monthOfBirth like :monthOfBirth AND d.dateOfBirth like :dateOfBirth ";

        // format must be yyyy-mm-dd
        String[] params = dobStr.split("-");
        if (params.length != 3) {
            return null;
        }

        if (params.length != 3)
            return new ArrayList<Demographic>();

        if (statuses != null) {
            queryString += " and d.patientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }

        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        EntityManager session = entityManager();
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("yearOfBirth", params[0].trim() + "%");
            q.setParameter("monthOfBirth", params[1].trim() + "%");
            q.setParameter("dateOfBirth", params[2].trim() + "%");

            if (statuses != null) {
                q.setParameter("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }

            list = q.getResultList();
        return list;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public List<Demographic> searchMergedDemographicByDOB(String dobStr, int limit, int offset, String providerNo,
                                                          boolean outOfDomain) {
        List<Demographic> list = new ArrayList<Demographic>();
        String queryString = "From Demographic d where d.yearOfBirth like :yearOfBirth AND d.monthOfBirth like :monthOfBirth AND d.dateOfBirth like :dateOfBirth and d.headRecord is not null ";

        // format must be yyyy-mm-dd
        String[] params = dobStr.split("-");
        if (params.length != 3)
            return new ArrayList<Demographic>();

        EntityManager session = entityManager();
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("yearOfBirth", params[0].trim() + "%");
            q.setParameter("monthOfBirth", params[1].trim() + "%");
            q.setParameter("dateOfBirth", params[2].trim() + "%");

            list = q.getResultList();
        return list;
    }

    @Override
    public List<Demographic> searchDemographicByPhone(String phoneStr, int limit, int offset, String providerNo,
                                                      boolean outOfDomain) {
        List<Demographic> demographics = searchDemographicByPhoneAndStatus(phoneStr, null, limit, offset, null,
            providerNo, outOfDomain, false);
        demographics.addAll(searchDemographicByExtKeyAndValueLike(DemographicExt.DemographicProperty.demo_cell,
            phoneStr, limit, offset, null, providerNo, outOfDomain));
        return demographics;
    }

    @Override
    public List<Demographic> searchDemographicByPhoneAndNotStatus(String phoneStr, List<String> statuses, int limit,
                                                                  int offset, String providerNo, boolean outOfDomain) {
        List<Demographic> demographics = searchDemographicByPhoneAndStatus(phoneStr, statuses, limit, offset, null,
            providerNo, outOfDomain, true);
        demographics
            .addAll(searchDemographicByExtKeyAndValueLikeAndNotStatus(DemographicExt.DemographicProperty.demo_cell,
                phoneStr, statuses, limit, offset, null, providerNo, outOfDomain));
        return demographics;

    }

    @Override
    public List<Demographic> searchDemographicByPhoneAndStatus(String phoneStr, List<String> statuses, int limit,
                                                               int offset, String providerNo, boolean outOfDomain) {
        List<Demographic> demographics = searchDemographicByPhoneAndStatus(phoneStr, statuses, limit, offset, null,
            providerNo, outOfDomain, false);
        demographics.addAll(searchDemographicByExtKeyAndValueLikeAndStatus(DemographicExt.DemographicProperty.demo_cell,
            phoneStr, statuses, limit, offset, null, providerNo, outOfDomain));
        return demographics;
    }

    @Override
    public List<Demographic> searchDemographicByPhone(String phoneStr, int limit, int offset, String orderBy,
                                                      String providerNo, boolean outOfDomain) {
        List<Demographic> demographics = searchDemographicByPhoneAndStatus(phoneStr, null, limit, offset, orderBy,
            providerNo, outOfDomain, false);
        demographics.addAll(searchDemographicByExtKeyAndValueLike(DemographicExt.DemographicProperty.demo_cell,
            phoneStr, limit, offset, orderBy, providerNo, outOfDomain));
        return demographics;
    }

    @Override
    public List<Demographic> searchDemographicByPhoneAndNotStatus(String phoneStr, List<String> statuses, int limit,
                                                                  int offset, String orderBy, String providerNo, boolean outOfDomain) {
        List<Demographic> demographics = searchDemographicByPhoneAndStatus(phoneStr, statuses, limit, offset, orderBy,
            providerNo, outOfDomain, true);
        demographics
            .addAll(searchDemographicByExtKeyAndValueLikeAndNotStatus(DemographicExt.DemographicProperty.demo_cell,
                phoneStr, statuses, limit, offset, orderBy, providerNo, outOfDomain));
        return demographics;
    }

    @Override
    public List<Demographic> searchDemographicByPhoneAndStatus(String phoneStr, List<String> statuses, int limit,
                                                               int offset, String orderBy, String providerNo, boolean outOfDomain) {
        List<Demographic> demographics = searchDemographicByPhoneAndStatus(phoneStr, statuses, limit, offset, orderBy,
            providerNo, outOfDomain, false);
        demographics.addAll(searchDemographicByExtKeyAndValueLikeAndStatus(DemographicExt.DemographicProperty.demo_cell,
            phoneStr, statuses, limit, offset, orderBy, providerNo, outOfDomain));
        return demographics;
    }

    @Override
    public List<Demographic> searchDemographicByPhoneAndStatus(String phoneStr, List<String> statuses, int limit,
                                                               int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses) {
        return searchDemographicByPhoneAndStatus(phoneStr, statuses, limit, offset, orderBy,
            providerNo, outOfDomain, ignoreStatuses, true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchDemographicByPhoneAndStatus(String phoneStr, List<String> statuses, int limit,
                                                               int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses,
                                                               boolean ignoreMerged) {
        List<Demographic> list = new ArrayList<Demographic>();
        String queryString = "From Demographic d where (d.phone like :phone OR d.phone2 LIKE :phone)";

        if (statuses != null) {
            queryString += " and d.patientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }
        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        EntityManager session = entityManager();
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("phone", "%" + phoneStr.trim() + "%");

            if (statuses != null) {
                q.setParameter("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }

            list = q.getResultList();
        return list;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchMergedDemographicByPhone(String phoneStr, int limit, int offset, String providerNo,
                                                            boolean outOfDomain) {
        List<Demographic> list = new ArrayList<Demographic>();

        String queryString = "From Demographic d where d.phone like :phone and d.headRecord is not null ";
        EntityManager session = entityManager();
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("phone", "%" + phoneStr.trim() + "%");

            list = q.getResultList();
        return list;
    }

    @Override
    public List<Demographic> searchDemographicByHIN(String hinStr) {
        List<Demographic> list = new ArrayList<Demographic>();

        String queryString = "From Demographic d where d.hin like :hin and d.patientStatus != 'MERGED' ";

        EntityManager session = entityManager();
            Query q = session.createQuery(queryString);

            q.setParameter("hin", hinStr.trim());

            list = q.getResultList();
        return list;
    }

    @Override
    public List<Demographic> searchDemographicByHIN(String hinStr, int limit, int offset, String providerNo,
                                                    boolean outOfDomain) {
        return searchDemographicByHINAndStatus(hinStr, null, limit, offset, null, providerNo, outOfDomain, false);
    }

    @Override
    public List<Demographic> searchDemographicByHINAndNotStatus(String hinStr, List<String> statuses, int limit,
                                                                int offset, String providerNo, boolean outOfDomain) {
        return searchDemographicByHINAndStatus(hinStr, statuses, limit, offset, null, providerNo, outOfDomain, true);
    }

    @Override
    public List<Demographic> searchDemographicByHINAndStatus(String hinStr, List<String> statuses, int limit,
                                                             int offset, String providerNo, boolean outOfDomain) {
        return searchDemographicByHINAndStatus(hinStr, statuses, limit, offset, null, providerNo, outOfDomain, false);
    }

    @Override
    public List<Demographic> searchDemographicByHIN(String hinStr, int limit, int offset, String orderBy,
                                                    String providerNo, boolean outOfDomain) {
        return searchDemographicByHINAndStatus(hinStr, null, limit, offset, orderBy, providerNo, outOfDomain, false);
    }

    @Override
    public List<Demographic> searchDemographicByHINAndNotStatus(String hinStr, List<String> statuses, int limit,
                                                                int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return searchDemographicByHINAndStatus(hinStr, statuses, limit, offset, orderBy, providerNo, outOfDomain, true);
    }

    @Override
    public List<Demographic> searchDemographicByHINAndStatus(String hinStr, List<String> statuses, int limit,
                                                             int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return searchDemographicByHINAndStatus(hinStr, statuses, limit, offset, orderBy, providerNo, outOfDomain,
            false);
    }

    @Override
    public List<Demographic> searchDemographicByHINAndStatus(String hinStr, List<String> statuses, int limit,
                                                             int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses) {
        return searchDemographicByHINAndStatus(hinStr, statuses, limit, offset, orderBy, providerNo, outOfDomain,
            ignoreStatuses, true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchDemographicByHINAndStatus(String hinStr, List<String> statuses, int limit,
                                                             int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses,
                                                             boolean ignoreMerged) {
        List<Demographic> list = new ArrayList<Demographic>();

        String queryString = "From Demographic d where d.hin like :hin ";

        if (statuses != null) {
            queryString += " and d.patientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }

        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        EntityManager session = entityManager();
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("hin", hinStr.trim() + "%");

            if (statuses != null) {
                q.setParameter("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }

            list = q.getResultList();
        return list;
    }

    /**
     * All search parameters can be null to ignore that parameter (limit parameters
     * can not be null).
     * The result is an AND of all non-null attributes. i.e. if gender and first
     * name are provided
     * then only results where both match are returned. You must provide at least 1
     * non-null
     * parameter.
     *
     * @param hin           it will do a substring match
     * @param firstName     it will do a substring match
     * @param lastName      it will do a substring match
     * @param gender        it will do an exact match
     * @param dateOfBirth   it will do an exact match
     * @param city          it will do an substring match
     * @param province      it will do an exact match
     * @param phone         it will do an substring match
     * @param email         it will do an substring match
     * @param alias         it will do an substring match
     * @param startIndex    index of the first result
     * @param itemsToReturn number of items to return
     */
    @Override
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
        int itemsToReturn) {
        return findByAttributes(
            hin,
            firstName,
            lastName,
            gender,
            dateOfBirth,
            city,
            province,
            phone,
            email,
            alias,
            startIndex,
            itemsToReturn,
            false);
    }

    /**
     * All search parameters can be null to ignore that parameter (limit parameters
     * can not be null).
     * The result is an AND of all non-null attributes. i.e. if gender and first
     * name are provided
     * then only results where both match are returned. You must provide at least 1
     * non-null
     * parameter.
     *
     * @param hin           it will do a substring match
     * @param firstName     it will do a substring match against both the FirstName
     *                      and Alias fields (i.e. matches if either field contains the value).
     *                      If both {@code firstName} and {@code alias} are non-null, the Alias
     *                      field must satisfy both constraints simultaneously, so callers should
     *                      pass {@code alias=null} when using {@code firstName} to avoid
     *                      over-constraining the search.
     * @param lastName      it will do a substring match
     * @param gender        it will do an exact match
     * @param dateOfBirth   it will do an exact match
     * @param city          it will do an substring match
     * @param province      it will do an exact match
     * @param phone         it will do an substring match
     * @param email         it will do an substring match
     * @param alias         it will do a substring match against the Alias field only
     * @param startIndex    index of the first result
     * @param itemsToReturn number of items to return
     * @param orderByName   order by last name and first name
     * @return List&lt;Demographic&gt; matching records, paginated by {@code startIndex} and
     *         {@code itemsToReturn}, ordered by last name and first name when
     *         {@code orderByName} is {@code true}
     * @throws IllegalArgumentException if all searchable parameters are {@code null}
     */
    @Override
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
        boolean orderByName) {
        // here we build the sql where clause, to simplify the logic we just append all
        // parameters, then after we'll strip out the first " and" as the logic is
        // easier then checking if we have to add "and" for every parameter.
        String sqlCommand = null;
        StringBuilder sqlParameters = new StringBuilder();

        if (hin != null)
            sqlParameters.append(" and d.hin like :hin");
        if (firstName != null)
            sqlParameters.append(" and (d.firstName like :firstName or d.alias like :firstName)");
        if (lastName != null)
            sqlParameters.append(" and d.lastName like :lastName");
        if (gender != null)
            sqlParameters.append(" and d.sex = :gender");

        if (dateOfBirth != null) {
            sqlParameters.append(" and d.yearOfBirth = :yearOfBirth");
            sqlParameters.append(" and d.monthOfBirth = :monthOfBirth");
            sqlParameters.append(" and d.dateOfBirth = :dateOfBirth");
        }

        if (city != null)
            sqlParameters.append(" and d.city like :city");
        if (province != null)
            sqlParameters.append(" and d.province = :province");
        if (phone != null)
            sqlParameters.append(" and (d.phone like :phone or d.phone2 like :phone)");
        if (email != null)
            sqlParameters.append(" and d.email like :email");
        if (alias != null)
            sqlParameters.append(" and d.alias like :alias");

        // at least 1 parameter must exist
        // we remove the first " and" because the first clause is after the "where" in
        // the sql statement.
        if (sqlParameters.length() == 0)
            throw (new IllegalArgumentException("at least one parameter must be present"));
        else {
            sqlCommand = "from Demographic d where"
                + sqlParameters.substring(" and".length(), sqlParameters.length())
                + (orderByName ? " order by d.lastName, d.firstName" : "");
        }

        EntityManager session = entityManager();
            Query query = session.createQuery(sqlCommand);

            if (hin != null)
                query.setParameter("hin", "%" + hin + "%");
            if (firstName != null)
                query.setParameter("firstName", "%" + firstName + "%");
            if (lastName != null)
                query.setParameter("lastName", "%" + lastName + "%");
            if (gender != null)
                query.setParameter("gender", gender.name());

            if (dateOfBirth != null) {
                query.setParameter("yearOfBirth", ensure2DigitDateHack(dateOfBirth.get(Calendar.YEAR)));
                query.setParameter("monthOfBirth", ensure2DigitDateHack(dateOfBirth.get(Calendar.MONTH) + 1));
                query.setParameter("dateOfBirth", ensure2DigitDateHack(dateOfBirth.get(Calendar.DAY_OF_MONTH)));
            }

            if (city != null)
                query.setParameter("city", "%" + city + "%");
            if (province != null)
                query.setParameter("province", province);
            if (phone != null)
                query.setParameter("phone", "%" + phone + "%");
            if (email != null)
                query.setParameter("email", "%" + email + "%");
            if (alias != null)
                query.setParameter("alias", "%" + alias + "%");

            query.setFirstResult(startIndex);
            query.setMaxResults(itemsToReturn);

            @SuppressWarnings("unchecked")
            List<Demographic> results = query.getResultList();

            return (results);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchMergedDemographicByHIN(String hinStr, int limit, int offset, String providerNo,
                                                          boolean outOfDomain) {
        List<Demographic> list = new ArrayList<Demographic>();

        String queryString = "From Demographic d where d.hin like :hin and d.headRecord is not null ";
        EntityManager session = entityManager();
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("hin", hinStr.trim() + "%");

            list = q.getResultList();
        return list;
    }

    @Override
    public List<Demographic> searchDemographicByAddress(String addressStr, int limit, int offset, String providerNo,
                                                        boolean outOfDomain) {
        return searchDemographicByAddressAndStatus(addressStr, null, limit, offset, null, providerNo, outOfDomain,
            false);
    }

    @Override
    public List<Demographic> searchDemographicByAddressAndStatus(String addressStr, List<String> statuses, int limit,
                                                                 int offset, String providerNo, boolean outOfDomain) {
        return searchDemographicByAddressAndStatus(addressStr, statuses, limit, offset, null, providerNo, outOfDomain,
            false);
    }

    @Override
    public List<Demographic> searchDemographicByAddressAndNotStatus(String addressStr, List<String> statuses, int limit,
                                                                    int offset, String providerNo, boolean outOfDomain) {
        return searchDemographicByAddressAndStatus(addressStr, statuses, limit, offset, null, providerNo, outOfDomain,
            true);
    }

    @Override
    public List<Demographic> searchDemographicByAddress(String addressStr, int limit, int offset, String orderBy,
                                                        String providerNo, boolean outOfDomain) {
        return searchDemographicByAddressAndStatus(addressStr, null, limit, offset, orderBy, providerNo, outOfDomain,
            false);
    }

    @Override
    public List<Demographic> searchDemographicByAddressAndStatus(String addressStr, List<String> statuses, int limit,
                                                                 int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return searchDemographicByAddressAndStatus(addressStr, statuses, limit, offset, orderBy, providerNo,
            outOfDomain, false);
    }

    @Override
    public List<Demographic> searchDemographicByAddressAndNotStatus(String addressStr, List<String> statuses, int limit,
                                                                    int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return searchDemographicByAddressAndStatus(addressStr, statuses, limit, offset, orderBy, providerNo,
            outOfDomain, true);
    }

    @Override
    public List<Demographic> searchDemographicByAddressAndStatus(String addressStr, List<String> statuses, int limit,
                                                                 int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses) {
        return searchDemographicByAddressAndStatus(addressStr, statuses, limit, offset, orderBy,
            providerNo, outOfDomain, ignoreStatuses, true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchDemographicByAddressAndStatus(String addressStr, List<String> statuses, int limit,
                                                                 int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses,
                                                                 boolean ignoreMerged) {
        List<Demographic> list = new ArrayList<Demographic>();

        String queryString = "From Demographic d where d.address like :address ";

        if (statuses != null) {
            queryString += " and d.patientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }

        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        EntityManager session = entityManager();
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("address", "%" + addressStr.trim() + "%");

            if (statuses != null) {
                q.setParameter("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }
            list = q.getResultList();
        return list;
    }

    @Override
    public List<Demographic> searchDemographicByExtKeyAndValueLike(DemographicExt.DemographicProperty key, String value,
                                                                   int limit, int offset, String providerNo, boolean outOfDomain) {
        return searchDemographicByExtKeyAndValueLikeAndStatus(key, value, null, limit, offset, null, providerNo,
            outOfDomain, false);
    }

    @Override
    public List<Demographic> searchDemographicByExtKeyAndValueLikeAndNotStatus(DemographicExt.DemographicProperty key,
                                                                               String value, List<String> statuses, int limit, int offset, String providerNo, boolean outOfDomain) {
        return searchDemographicByExtKeyAndValueLikeAndStatus(key, value, statuses, limit, offset, null, providerNo,
            outOfDomain, true);
    }

    @Override
    public List<Demographic> searchDemographicByExtKeyAndValueLikeAndStatus(DemographicExt.DemographicProperty key,
                                                                            String value, List<String> statuses, int limit, int offset, String providerNo, boolean outOfDomain) {
        return searchDemographicByExtKeyAndValueLikeAndStatus(key, value, statuses, limit, offset, null, providerNo,
            outOfDomain, false);
    }

    @Override
    public List<Demographic> searchDemographicByExtKeyAndValueLike(DemographicExt.DemographicProperty key, String value,
                                                                   int limit, int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return searchDemographicByExtKeyAndValueLikeAndStatus(key, value, null, limit, offset, orderBy, providerNo,
            outOfDomain, false);
    }

    @Override
    public List<Demographic> searchDemographicByExtKeyAndValueLikeWithMerged(DemographicExt.DemographicProperty key,
                                                                             String value, int limit, int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return searchDemographicByExtKeyAndValueLikeAndStatus(key, value, null, limit, offset, orderBy, providerNo,
            outOfDomain, false, false);
    }

    @Override
    public List<Demographic> searchDemographicByExtKeyAndValueLikeAndNotStatus(DemographicExt.DemographicProperty key,
                                                                               String value, List<String> statuses, int limit, int offset, String orderBy, String providerNo,
                                                                               boolean outOfDomain) {
        return searchDemographicByExtKeyAndValueLikeAndStatus(key, value, statuses, limit, offset, orderBy, providerNo,
            outOfDomain, true);
    }

    @Override
    public List<Demographic> searchDemographicByExtKeyAndValueLikeAndStatus(DemographicExt.DemographicProperty key,
                                                                            String value, List<String> statuses, int limit, int offset, String orderBy, String providerNo,
                                                                            boolean outOfDomain) {
        return searchDemographicByExtKeyAndValueLikeAndStatus(key, value, statuses, limit, offset, orderBy, providerNo,
            outOfDomain, false);
    }

    @Override
    public List<Demographic> searchDemographicByExtKeyAndValueLikeAndStatus(DemographicExt.DemographicProperty key,
                                                                            String value, List<String> statuses, int limit, int offset, String orderBy, String providerNo,
                                                                            boolean outOfDomain, boolean ignoreStatuses) {
        return searchDemographicByExtKeyAndValueLikeAndStatus(key, value, statuses, limit, offset, orderBy, providerNo,
            outOfDomain, ignoreStatuses, true);
    }

    @Override
    public List<Demographic> searchDemographicByExtKeyAndValueLikeAndStatus(DemographicExt.DemographicProperty key,
                                                                            String value, List<String> statuses, int limit, int offset, String orderBy, String providerNo,
                                                                            boolean outOfDomain, boolean ignoreStatuses, boolean ignoreMerged) {
        List<Demographic> list = new ArrayList<Demographic>();
        String queryString = "SELECT {de.*} FROM demographic de "
            + "INNER JOIN demographicExt dext ON (dext.demographic_no=de.demographic_no) "
            + "WHERE dext.key_val=:key "
            + "AND dext.value LIKE :value";

        if (statuses != null) {
            queryString += " AND de.patient_status " + ((ignoreStatuses) ? "NOT" : "") + "  IN (:statuses)";
        }

        if (ignoreMerged) {
            queryString += " AND de.patient_status != 'MERGED'";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND de.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }

        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy, true);
        }

        EntityManager session = entityManager();
            NativeQuery query = session.createNativeQuery(queryString).unwrap(NativeQuery.class);
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            query.setParameter("key", key.name());
            query.setParameter("value", "%" + value + "%");

            if (statuses != null) {
                query.setParameter("statuses", statuses);
            }
            if (providerNo != null && !outOfDomain) {
                query.setParameter("providerNo", providerNo);
            }

            query.addEntity("de", Demographic.class);
            list = query.getResultList();
        return list;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchMergedDemographicByAddress(String addressStr, int limit, int offset,
                                                              String providerNo, boolean outOfDomain) {
        List<Demographic> list = new ArrayList<Demographic>();

        String queryString = "From Demographic d where d.address like :address and d.headRecord is not null ";

        EntityManager session = entityManager();
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("address", addressStr.trim() + "%");

            list = q.getResultList();
        return list;
    }

    @Override
    public List<Demographic> findDemographicByChartNo(String chartNoStr, int limit, int offset, String providerNo,
                                                      boolean outOfDomain) {
        return findDemographicByChartNoAndStatus(chartNoStr, null, limit, offset, null, providerNo, outOfDomain, false);
    }

    @Override
    public List<Demographic> findDemographicByChartNoAndStatus(String chartNoStr, List<String> statuses, int limit,
                                                               int offset, String providerNo, boolean outOfDomain) {
        return findDemographicByChartNoAndStatus(chartNoStr, statuses, limit, offset, null, providerNo, outOfDomain,
            false);
    }

    @Override
    public List<Demographic> findDemographicByChartNoAndNotStatus(String chartNoStr, List<String> statuses, int limit,
                                                                  int offset, String providerNo, boolean outOfDomain) {
        return findDemographicByChartNoAndStatus(chartNoStr, statuses, limit, offset, null, providerNo, outOfDomain,
            true);
    }

    @Override
    public List<Demographic> findDemographicByChartNo(String chartNoStr, int limit, int offset, String orderBy,
                                                      String providerNo, boolean outOfDomain) {
        return findDemographicByChartNoAndStatus(chartNoStr, null, limit, offset, orderBy, providerNo, outOfDomain,
            false);
    }

    @Override
    public List<Demographic> findDemographicByChartNoAndStatus(String chartNoStr, List<String> statuses, int limit,
                                                               int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return findDemographicByChartNoAndStatus(chartNoStr, statuses, limit, offset, orderBy, providerNo, outOfDomain,
            false);
    }

    @Override
    public List<Demographic> findDemographicByChartNoAndNotStatus(String chartNoStr, List<String> statuses, int limit,
                                                                  int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return findDemographicByChartNoAndStatus(chartNoStr, statuses, limit, offset, orderBy, providerNo, outOfDomain,
            true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> findDemographicByChartNoAndStatus(String chartNoStr, List<String> statuses, int limit,
                                                               int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses) {

        String queryString = "From Demographic d where d.chartNo like :chartNo ";

        if (statuses != null) {
            queryString += " and d.patientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }

        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        EntityManager session = entityManager();
        List<Demographic> list = null;

            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("chartNo", chartNoStr.trim() + "%");

            if (statuses != null) {
                q.setParameter("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }
            list = q.getResultList();
        return list;
    }

    @Override
    public List<Demographic> findDemographicByDemographicNo(String demographicNoStr, int limit, int offset,
                                                            String providerNo, boolean outOfDomain) {
        return findDemographicByDemographicNoAndStatus(demographicNoStr, null, limit, offset, null, providerNo,
            outOfDomain, false);
    }

    @Override
    public List<Demographic> findDemographicByDemographicNoAndStatus(String demographicNoStr, List<String> statuses,
                                                                     int limit, int offset, String providerNo, boolean outOfDomain) {
        return findDemographicByDemographicNoAndStatus(demographicNoStr, statuses, limit, offset, null, providerNo,
            outOfDomain, false);
    }

    @Override
    public List<Demographic> findDemographicByDemographicNoAndNotStatus(String demographicNoStr, List<String> statuses,
                                                                        int limit, int offset, String providerNo, boolean outOfDomain) {
        return findDemographicByDemographicNoAndStatus(demographicNoStr, statuses, limit, offset, null, providerNo,
            outOfDomain, true);
    }

    @Override
    public List<Demographic> findDemographicByDemographicNo(String demographicNoStr, int limit, int offset,
                                                            String orderBy, String providerNo, boolean outOfDomain) {
        return findDemographicByDemographicNoAndStatus(demographicNoStr, null, limit, offset, orderBy, providerNo,
            outOfDomain, false);
    }

    @Override
    public List<Demographic> findDemographicByDemographicNoAndStatus(String demographicNoStr, List<String> statuses,
                                                                     int limit, int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return findDemographicByDemographicNoAndStatus(demographicNoStr, statuses, limit, offset, orderBy, providerNo,
            outOfDomain, false);
    }

    @Override
    public List<Demographic> findDemographicByDemographicNoAndNotStatus(String demographicNoStr, List<String> statuses,
                                                                        int limit, int offset, String orderBy, String providerNo, boolean outOfDomain) {
        return findDemographicByDemographicNoAndStatus(demographicNoStr, statuses, limit, offset, orderBy, providerNo,
            outOfDomain, true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> findDemographicByDemographicNoAndStatus(String demographicNoStr, List<String> statuses,
                                                                     int limit, int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses) {
        String queryString = "From Demographic d where 1 = 1 ";
        Integer val = null;
        try {
            val = Integer.valueOf(demographicNoStr.trim());
        } catch (NumberFormatException e) {
            // ignore
        }
        if (val != null) {
            queryString += " and d.demographicNo = :demographicNo ";
        }
        if (statuses != null) {
            queryString += " and d.patientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }

        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        EntityManager session = entityManager();
        List<Demographic> list = null;

            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            if (val != null) {
                q.setParameter("demographicNo", val);
            }
            if (statuses != null) {
                q.setParameter("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }
            list = q.getResultList();
        return list;
    }

    @Override
    public void save(Demographic demographic) {
        if (demographic == null) {
            return;
        }

        boolean objExists = false;
        if (demographic.getDemographicNo() != null) {
            objExists = clientExistsThenEvict(demographic.getDemographicNo());
        }

        if (objExists) {
            entityManager().merge(demographic);
        } else {
            entityManager().persist(demographic);
        }

        if (CarlosProperties.getInstance().isHL7A04GenerationEnabled() && !objExists) {
            (new HL7A04Generator()).generateHL7A04(demographic);
        }

        // the new way
        if (objExists == false) {
            publisher.publishEvent(new DemographicCreateEvent(demographic, demographic.getDemographicNo()));
        } else {
            publisher.publishEvent(new DemographicUpdateEvent(demographic, demographic.getDemographicNo()));
        }

    }

    @Override
    public String getOrderField(String orderBy, boolean nativeQuery) {
        if (!nativeQuery) {
            return getOrderField(orderBy);
        }

        String orderByField = "de.last_name, de.first_name";

        if ("last_name".equals(orderBy) || "last_name, first_name".equals(orderBy)) {
            orderByField = "de.last_name, de.first_name";
        } else if ("demographic_no".equals(orderBy)) {
            orderByField = "de.demographic_no";
        } else if ("chart_no".equals(orderBy)) {
            orderByField = "de.chart_no";
        } else if ("sex".equals(orderBy)) {
            orderByField = "de.sex";
        } else if ("dob".equals(orderBy)) {
            orderByField = "de.year_of_birth, de.month_of_birth, de.date_of_birth";
        } else if ("provider_no".equals(orderBy)) {
            orderByField = "de.provider_no";
        } else if ("roster_status".equals(orderBy)) {
            orderByField = "de.roster_status";
        } else if ("patient_status".equals(orderBy)) {
            orderByField = "de.patient_status";
        } else if ("phone".equals(orderBy)) {
            orderByField = "de.phone";
        }

        return orderByField;
    }

    @Override
    public String getOrderField(String orderBy) {

        String orderByField = "d.lastName,d.firstName";

        if (orderBy.equals("last_name") || orderBy.equals("last_name, first_name")) {
            orderByField = "d.lastName, d.firstName";
        } else if (orderBy.equals("demographic_no")) {
            orderByField = "d.demographicNo";
        } else if (orderBy.equals("chart_no")) {
            orderByField = "d.chartNo";
        } else if (orderBy.equals("sex")) {
            orderByField = "d.sex";
        } else if (orderBy.equals("dob")) {
            orderByField = "d.dateOfBirth";
        } else if (orderBy.equals("provider_no")) {
            orderByField = "d.providerNo";
        } else if (orderBy.equals("roster_status")) {
            orderByField = "d.rosterStatus";
        } else if (orderBy.equals("patient_status")) {
            orderByField = "d.patientStatus";
        } else if (orderBy.equals("phone")) {
            orderByField = "d.phone";
        }
        return orderByField;
    }

    @Override
    public List<Integer> getDemographicIdsAlteredSinceTime(Date value) {
        String sql = "SELECT DISTINCT demographic_no FROM log WHERE dateTime >= :cutoff AND action != 'read'";
        Query query = entityManager().createNativeQuery(sql);
        query.setParameter("cutoff", new Timestamp(value.getTime()));

        @SuppressWarnings("unchecked")
        List<Object> rows = query.getResultList();
        ArrayList<Integer> results = new ArrayList<Integer>();
        for (Object row : rows) {
            int id = ((Number) row).intValue();
            // Skip demographic_no=0 placeholder rows (preserves legacy behaviour).
            if (id != 0) {
                results.add(id);
            }
        }
        return results;
    }

    @Override
    public List<Integer> getDemographicIdsOpenedChartSinceTime(String value) {
        String sql = "SELECT DISTINCT contentId FROM log WHERE dateTime >= :cutoff AND content = 'eChart' GROUP BY contentId";
        Query query = entityManager().createNativeQuery(sql);
        query.setParameter("cutoff", value);

        @SuppressWarnings("unchecked")
        List<Object> rows = query.getResultList();
        ArrayList<Integer> results = new ArrayList<Integer>();
        for (Object row : rows) {
            results.add(((Number) row).intValue());
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getRosterStatuses() {
        List<String> results = (List<String>) JpqlQueryHelper.find(entityManager(),
            "SELECT DISTINCT d.rosterStatus FROM Demographic d where d.rosterStatus != '' and d.rosterStatus != 'RO' and d.rosterStatus != 'TE' and d.rosterStatus != 'FS'");
        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getAllRosterStatuses() {
        List<String> results = (List<String>) JpqlQueryHelper.find(entityManager(),
            "SELECT DISTINCT d.rosterStatus FROM Demographic d where d.rosterStatus is not null order by d.rosterStatus");
        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getAllPatientStatuses() {
        List<String> results = (List<String>) JpqlQueryHelper.find(entityManager(),
            "SELECT DISTINCT d.patientStatus FROM Demographic d where d.patientStatus is not null order by d.patientStatus");
        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> search_ptstatus() {
        List<String> results = (List<String>) JpqlQueryHelper.find(entityManager(),
            "SELECT DISTINCT d.patientStatus FROM Demographic d where d.patientStatus is not null and d.patientStatus <> '' and d.patientStatus <> 'AC' and d.patientStatus <> 'IN' and d.patientStatus <> 'DE' and d.patientStatus <> 'MO' and d.patientStatus <> 'FI' order by d.patientStatus");
        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getAllProviderNumbers() {
        List<String> results = (List<String>) JpqlQueryHelper.find(entityManager(),
            "SELECT DISTINCT d.providerNo FROM Demographic d order by d.providerNo");
        return results;
    }

    /// ////////////// CLIENT DAO MERGED ///////////////////////////

    /*
     * (non-Javadoc)
     *
     * @see io.github.carlos_emr.carlos.PMmodule.daos.DemographicDao#clientExists(java.lang.Integer)
     */
    @Override
    public boolean clientExists(Integer demographicNo) {

        boolean exists = entityManager().find(Demographic.class, demographicNo) != null;
        log.debug("exists: " + exists);

        return exists;
    }

    /**
     * Helper method.
     * <p>
     * Not using 'clientExists' because it doesn't 'evict' the demographic, which
     * causes errors when 'saveOrUpdate' is called
     * and the demographic already exists in the Hibernate cache.
     */
    @Override
    public boolean clientExistsThenEvict(Integer demographicNo) {
        boolean exists = false;

        Demographic existingDemo = this.getClientByDemographicNo(demographicNo);

        exists = (existingDemo != null);

        if (exists)
            entityManager().detach(existingDemo);

        log.debug("exists (then evict): " + exists);

        return exists;
    }

    @Override
    public Demographic getClientByDemographicNo(Integer demographicNo) {

        if (demographicNo == null || demographicNo.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        Demographic result = entityManager().find(Demographic.class, demographicNo);

        if (log.isDebugEnabled()) {
            log.debug("getClientByDemographicNo: id=" + demographicNo + ", found=" + (result != null));
        }

        return result;
    }

    @Override
    public List<Demographic> getClients() {
        log.error(
            "No one should be calling this method, this is a good way to run out of memory and crash a server... this is too large of a result set, it should be pagenated.",
            new IllegalArgumentException("The entire demographic table is too big to allow a full select."));

        String queryStr = " FROM Demographic";
        @SuppressWarnings("unchecked")
        List<Demographic> rs = (List<Demographic>) JpqlQueryHelper.find(entityManager(), queryStr);

        if (log.isDebugEnabled()) {
            log.debug("getClients: # of results=" + rs.size());
        }

        return rs;
    }

    // Quatro Merge
    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> search(ClientSearchFormBean bean, boolean returnOptinsOnly, boolean excludeMerged) {
        EntityManager session = entityManager();

        String firstName = "";
        String lastName = "";
        String firstNameL = "";
        String lastNameL = "";

        if (bean.getFirstName() != null && bean.getFirstName().length() > 0) {
            firstName = bean.getFirstName();
            firstNameL = firstName + "%";
        }

        if (bean.getLastName() != null && bean.getLastName().length() > 0) {
            lastName = bean.getLastName();
            lastNameL = lastName + "%";
        }

        String clientNo = bean.getDemographicNo();

        // Short-circuit: search by demographic number only
        if (clientNo != null && !"".equals(clientNo)) {
            if (Utility.IsInt(clientNo)) {
                String hql = "FROM Demographic d WHERE d.demographicNo = :demoNo";
                if (excludeMerged) {
                    hql += " AND d.merged = false";
                }
                return session.createQuery(hql, Demographic.class)
                    .setParameter("demoNo", Integer.valueOf(clientNo))
                    .getResultList();
            } else {
                return new ArrayList<Demographic>();
            }
        }

        String hql = "FROM Demographic d WHERE 1=1";
        Map<String, Object> params = new HashMap<>();

        if (excludeMerged) {
            hql += " AND d.merged = false";
        }

        if (firstName.length() > 0) {
            hql += " AND (lower(d.lastName) like lower(:fnLike) OR lower(d.alias) like lower(:fnLike) OR lower(d.firstName) like lower(:fnLike))";
            params.put("fnLike", firstNameL);
        }
        if (lastName.length() > 0) {
            hql += " AND (lower(d.firstName) like lower(:lnLike) OR lower(d.alias) like lower(:lnLike) OR lower(d.lastName) like lower(:lnLike))";
            params.put("lnLike", lastNameL);
        }

        if (bean.getDob() != null && bean.getDob().length() > 0) {
            Calendar cal = MyDateFormat.getCalendar(bean.getDob());
            if (cal != null) {
                hql += " AND d.dateOfBirth = :dob";
                params.put("dob", cal);
            }
        }

        if (bean.getHealthCardNumber() != null && bean.getHealthCardNumber().length() > 0) {
            hql += " AND d.hin = :hin";
            params.put("hin", bean.getHealthCardNumber());
        }

        if (bean.getHealthCardVersion() != null && bean.getHealthCardVersion().length() > 0) {
            hql += " AND d.ver = :ver";
            params.put("ver", bean.getHealthCardVersion());
        }

        if (bean.getAssignedToProviderNo() != null && bean.getAssignedToProviderNo().length() > 0) {
            hql += " AND d.demographicNo IN (SELECT a.clientId FROM Admission a WHERE a.primaryWorker = :assignedProvider)";
            params.put("assignedProvider", bean.getAssignedToProviderNo());
        }

        String active = bean.getActive();
        if ("1".equals(active)) {
            hql += " AND d.activeCount >= 1";
        } else if ("0".equals(active)) {
            hql += " AND d.activeCount = 0";
        }

        String gender = bean.getGender();
        if (gender != null && !"".equals(gender)) {
            hql += " AND d.sex = :gender";
            params.put("gender", gender);
        }

        hql += " ORDER BY d.lastName ASC, d.firstName ASC";

        var query = session.createQuery(hql, Demographic.class);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        List<Demographic> results = query.getResultList();

        if (log.isDebugEnabled()) {
            log.debug("search: # of results=" + results.size());
        }
        return results;
    }

    /*
     * use program_client table to do domain based search
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> search(ClientSearchFormBean bean) {

        EntityManager session = entityManager();

        String firstName = "";
        String lastName = "";
        String firstNameL = "";
        String lastNameL = "";

        if (bean.getFirstName() != null && bean.getFirstName().length() > 0) {
            firstName = bean.getFirstName();
            firstNameL = "%" + firstName + "%";
        }

        if (bean.getLastName() != null && bean.getLastName().length() > 0) {
            lastName = bean.getLastName();
            lastNameL = "%" + lastName + "%";
        }

        // Short-circuit: search by demographic number
        String clientNo = bean.getDemographicNo();
        if (clientNo != null && !"".equals(clientNo)) {
            if (Utility.IsInt(clientNo)) {
                String hql = "FROM Demographic d WHERE d.demographicNo = :demoNo";
                return session.createQuery(hql, Demographic.class)
                    .setParameter("demoNo", Integer.valueOf(clientNo))
                    .getResultList();
            } else {
                return new ArrayList<Demographic>();
            }
        }

        // For soundex mode, we must use a native SQL query because SOUNDEX() is MySQL-specific
        if (bean.isSearchUsingSoundex() && (firstName.length() > 0 || lastName.length() > 0)) {
            return searchWithSoundex(session, bean, firstName, lastName, firstNameL, lastNameL);
        }

        String hql = "FROM Demographic d WHERE (d.anonymous != 'one-time-anonymous' OR d.anonymous IS NULL)";
        Map<String, Object> params = new HashMap<>();

        if (bean.getChartNo() != null && bean.getChartNo().length() > 0) {
            hql += " AND d.chartNo like :chartNo";
            params.put("chartNo", "%" + bean.getChartNo() + "%");
        }

        if (firstName.length() > 0) {
            hql += " AND (lower(d.firstName) like lower(:fnLike) OR lower(d.alias) like lower(:fnLike))";
            params.put("fnLike", firstNameL);
        }
        if (lastName.length() > 0) {
            hql += " AND (lower(d.lastName) like lower(:lnLike) OR lower(d.alias) like lower(:lnLike))";
            params.put("lnLike", lastNameL);
        }

        if (bean.getDob() != null && bean.getDob().length() > 0) {
            hql += " AND d.yearOfBirth = :yob AND d.monthOfBirth = :mob AND d.dateOfBirth = :dayob";
            params.put("yob", bean.getYearOfBirth());
            params.put("mob", bean.getMonthOfBirth());
            params.put("dayob", bean.getDayOfBirth());
        }

        if (bean.getHealthCardNumber() != null && bean.getHealthCardNumber().length() > 0) {
            hql += " AND d.hin = :hin";
            params.put("hin", bean.getHealthCardNumber());
        }

        if (bean.getHealthCardVersion() != null && bean.getHealthCardVersion().length() > 0) {
            hql += " AND d.ver = :ver";
            params.put("ver", bean.getHealthCardVersion());
        }

        if (!bean.isSearchOutsideDomain()) {
            List<Integer> programIdList = buildProgramIdList(bean);
            if (programIdList.isEmpty()) {
                log.info("providers not staff in any program, ie. can't see ANYONE.");
                return new ArrayList<Demographic>();
            }
            hql += " AND d.demographicNo IN (SELECT a.clientId FROM Admission a WHERE a.programId IN (:programIds)";
            params.put("programIds", programIdList);

            if (bean.getDateFrom() != null && bean.getDateFrom().length() > 0) {
                Date dt = MyDateFormat.getSysDate(bean.getDateFrom().trim());
                hql += " AND a.admissionDate >= :dateFrom";
                params.put("dateFrom", dt);
            }
            if (bean.getDateTo() != null && bean.getDateTo().length() > 0) {
                Date dt1 = MyDateFormat.getSysDate(bean.getDateTo().trim());
                hql += " AND a.admissionDate <= :dateTo";
                params.put("dateTo", dt1);
            }
            hql += ")";
        }

        String active = bean.getActive();
        if ("1".equals(active)) {
            hql += " AND d.activeCount >= 1";
        } else if ("0".equals(active)) {
            hql += " AND d.activeCount = 0";
        }

        String gender = bean.getGender();
        if (gender != null && !"".equals(gender)) {
            hql += " AND d.sex = :gender";
            params.put("gender", gender);
        }

        var query = session.createQuery(hql, Demographic.class);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() instanceof List) {
                query.setParameter(entry.getKey(), (List<?>) entry.getValue());
            } else {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }

        List<Demographic> results = query.getResultList();

        if (log.isDebugEnabled()) {
            log.debug("search: # of results=" + results.size());
        }

        return results;
    }

    /**
     * Extracts program IDs from the bean's program domain list.
     */
    private List<Integer> buildProgramIdList(ClientSearchFormBean bean) {
        if (bean.getProgramDomain() == null) {
            bean.setProgramDomain(new ArrayList<ProgramProvider>());
        }
        List<Integer> programIdList = new ArrayList<>();
        for (int x = 0; x < bean.getProgramDomain().size(); x++) {
            ProgramProvider p = (ProgramProvider) bean.getProgramDomain().get(x);
            programIdList.add(p.getProgramId().intValue());
        }
        return programIdList;
    }

    /**
     * Soundex-based client search using native SQL (SOUNDEX is MySQL-specific).
     */
    @SuppressWarnings("unchecked")
    @NativeSql("demographic")
    private List<Demographic> searchWithSoundex(EntityManager session, ClientSearchFormBean bean,
                                                 String firstName, String lastName, String firstNameL, String lastNameL) {
        String sql = "SELECT d.* FROM demographic d WHERE (d.anonymous != 'one-time-anonymous' OR d.anonymous IS NULL)";
        Map<String, Object> params = new HashMap<>();

        if (bean.getChartNo() != null && bean.getChartNo().length() > 0) {
            sql += " AND d.chart_no like :chartNo";
            params.put("chartNo", "%" + bean.getChartNo() + "%");
        }

        if (firstName.length() > 0) {
            sql += " AND ((lower(d.first_name) like lower(:fnLike) OR lower(d.alias) like lower(:fnLike))"
                + " OR (LEFT(SOUNDEX(d.first_name),2) = LEFT(SOUNDEX(:fnSoundex),2))"
                + " OR (LEFT(SOUNDEX(d.alias),2) = LEFT(SOUNDEX(:fnSoundex),2)))";
            params.put("fnLike", firstNameL);
            params.put("fnSoundex", firstName);
        }
        if (lastName.length() > 0) {
            sql += " AND ((lower(d.last_name) like lower(:lnLike) OR lower(d.alias) like lower(:lnLike))"
                + " OR (LEFT(SOUNDEX(d.last_name),2) = LEFT(SOUNDEX(:lnSoundex),2))"
                + " OR (LEFT(SOUNDEX(d.alias),2) = LEFT(SOUNDEX(:lnSoundex),2)))";
            params.put("lnLike", lastNameL);
            params.put("lnSoundex", lastName);
        }

        if (bean.getDob() != null && bean.getDob().length() > 0) {
            sql += " AND d.year_of_birth = :yob AND d.month_of_birth = :mob AND d.date_of_birth = :dayob";
            params.put("yob", bean.getYearOfBirth());
            params.put("mob", bean.getMonthOfBirth());
            params.put("dayob", bean.getDayOfBirth());
        }

        if (bean.getHealthCardNumber() != null && bean.getHealthCardNumber().length() > 0) {
            sql += " AND d.hin = :hin";
            params.put("hin", bean.getHealthCardNumber());
        }

        if (bean.getHealthCardVersion() != null && bean.getHealthCardVersion().length() > 0) {
            sql += " AND d.ver = :ver";
            params.put("ver", bean.getHealthCardVersion());
        }

        if (!bean.isSearchOutsideDomain()) {
            List<Integer> programIdList = buildProgramIdList(bean);
            if (programIdList.isEmpty()) {
                log.info("providers not staff in any program, ie. can't see ANYONE.");
                return new ArrayList<Demographic>();
            }
            sql += " AND d.demographic_no IN (SELECT a.client_id FROM admission a WHERE a.program_id IN (:programIds)";
            params.put("programIds", programIdList);

            if (bean.getDateFrom() != null && bean.getDateFrom().length() > 0) {
                Date dt = MyDateFormat.getSysDate(bean.getDateFrom().trim());
                sql += " AND a.admission_date >= :dateFrom";
                params.put("dateFrom", dt);
            }
            if (bean.getDateTo() != null && bean.getDateTo().length() > 0) {
                Date dt1 = MyDateFormat.getSysDate(bean.getDateTo().trim());
                sql += " AND a.admission_date <= :dateTo";
                params.put("dateTo", dt1);
            }
            sql += ")";
        }

        String active = bean.getActive();
        if ("1".equals(active)) {
            sql += " AND (SELECT count(*) FROM admission a2 WHERE a2.client_id=d.demographic_no AND a2.admission_status='current' AND a2.program_id IN (SELECT p.id FROM program p WHERE p.type='Service')) >= 1";
        } else if ("0".equals(active)) {
            sql += " AND (SELECT count(*) FROM admission a2 WHERE a2.client_id=d.demographic_no AND a2.admission_status='current' AND a2.program_id IN (SELECT p.id FROM program p WHERE p.type='Service')) = 0";
        }

        String gender = bean.getGender();
        if (gender != null && !"".equals(gender)) {
            sql += " AND d.sex = :gender";
            params.put("gender", gender);
        }

        @SuppressWarnings("unchecked")
        NativeQuery<Demographic> nativeQuery = session.createNativeQuery(sql, Demographic.class)
            .unwrap(NativeQuery.class);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            // Hibernate's JPA mode accepts Collection values via setParameter for IN clauses,
            // so we no longer need the Hibernate-specific setParameterList.
            nativeQuery.setParameter(entry.getKey(), entry.getValue());
        }

        List<Demographic> results = nativeQuery.getResultList();

        if (log.isDebugEnabled()) {
            log.debug("search (soundex): # of results=" + results.size());
        }

        return results;
    }

    @Override
    public void saveClient(Demographic client) {

        if (client == null) {
            throw new IllegalArgumentException();
        }

        boolean objExists = false;
        if (client.getDemographicNo() != null)
            objExists = clientExistsThenEvict(client.getDemographicNo());

        client.setLastUpdateDate(new Date());
        if (objExists) {
            entityManager().merge(client);
        } else {
            entityManager().persist(client);
        }

        if (CarlosProperties.getInstance().isHL7A04GenerationEnabled() && !objExists)
            (new HL7A04Generator()).generateHL7A04(client);

        // the new way
        if (objExists == false) {
            publisher.publishEvent(new DemographicCreateEvent(client, client.getDemographicNo()));
        } else {
            publisher.publishEvent(new DemographicUpdateEvent(client, client.getDemographicNo()));
        }

        if (log.isDebugEnabled()) {
            log.debug("saveClient: id=" + client.getDemographicNo());
        }
    }

    public static class ClientListsReportResults {
        public int demographicId;
        public String firstName;
        public String lastName;
        public Calendar dateOfBirth;
        public int admissionId;
        public int programId;
        public String programName;
    }

    public Map<String, ClientListsReportResults> findByReportCriteria(ClientListsReportFormBean x) {

        boolean joinCaseMgmtNote = StringUtils.trimToNull(x.getProviderId()) != null
            || StringUtils.trimToNull(x.getSeenStartDate()) != null
            || StringUtils.trimToNull(x.getSeenEndDate()) != null;

        String admissionStatus = StringUtils.trimToNull(x.getAdmissionStatus());
        String providerId = StringUtils.trimToNull(x.getProviderId());
        String seenStartDate = StringUtils.trimToNull(x.getSeenStartDate());
        String seenEndDate = StringUtils.trimToNull(x.getSeenEndDate());
        String programId = StringUtils.trimToNull(x.getProgramId());
        String enrolledStartDate = StringUtils.trimToNull(x.getEnrolledStartDate());
        String enrolledEndDate = StringUtils.trimToNull(x.getEnrolledEndDate());

        // Explicit projection so downstream code can read by fixed positional index
        // (the old JDBC path read by "table.column" aliases on the ResultSet).
        String sql = String.join(" ",
                "select admission.am_id, demographic.year_of_birth, demographic.month_of_birth,",
                "demographic.date_of_birth, demographic.demographic_no, demographic.first_name,",
                "demographic.last_name, program.id as program_id, program.name as program_name",
                "from demographic,",
                (joinCaseMgmtNote ? "casemgmt_note," : ""),
                "admission, program",
                "where demographic.demographic_no = admission.client_id",
                (joinCaseMgmtNote ? "and demographic.demographic_no = casemgmt_note.demographic_no" : ""),
                "and admission.program_id = program.id",
                (admissionStatus != null ? "and demographic.patient_status = :admissionStatus" : ""),
                (providerId != null ? "and casemgmt_note.provider_no = :providerId" : ""),
                (seenStartDate != null ? "and casemgmt_note.update_date >= :seenStartDate" : ""),
                (seenEndDate != null ? "and casemgmt_note.update_date <= :seenEndDate" : ""),
                (programId != null ? "and admission.program_id = :programId" : ""),
                (enrolledStartDate != null ? "and admission.admission_date >= :enrolledStartDate" : ""),
                (enrolledEndDate != null ? "and admission.admission_date <= :enrolledEndDate" : ""),
                "order by last_name, first_name");

        Query query = entityManager().createNativeQuery(sql);
        if (admissionStatus != null) query.setParameter("admissionStatus", admissionStatus);
        if (providerId != null) query.setParameter("providerId", providerId);
        if (seenStartDate != null) query.setParameter("seenStartDate", seenStartDate);
        if (seenEndDate != null) query.setParameter("seenEndDate", seenEndDate);
        if (programId != null) query.setParameter("programId", programId);
        if (enrolledStartDate != null) query.setParameter("enrolledStartDate", enrolledStartDate);
        if (enrolledEndDate != null) query.setParameter("enrolledEndDate", enrolledEndDate);

        // yeah I know using a treeMap isn't an efficient way of making this unique but
        // given the current constraints this was quick and dirty and should work for
        // the size of our data set
        TreeMap<String, ClientListsReportResults> results = new TreeMap<String, ClientListsReportResults>();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        for (Object[] row : rows) {
            ClientListsReportResults r = new ClientListsReportResults();
            r.admissionId = ((Number) row[0]).intValue();

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(0);
            // year_of_birth / month_of_birth / date_of_birth are all VARCHAR columns,
            // so the JDBC driver returns String here (not a
            // Number as admission.am_id / demographic_no / program.id on the surrounding
            // rows do). Parse the numeric text explicitly — casting to Number would CCE.
            calendar.set(Calendar.YEAR, Integer.parseInt((String) row[1]));
            calendar.set(Calendar.MONTH, Integer.parseInt((String) row[2]) - 1);
            calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt((String) row[3]));
            r.dateOfBirth = calendar;

            r.demographicId = ((Number) row[4]).intValue();
            r.firstName = row[5] == null ? null : row[5].toString();
            r.lastName = row[6] == null ? null : row[6].toString();
            r.programId = ((Number) row[7]).intValue();
            r.programName = row[8] == null ? null : row[8].toString();

            results.put(r.lastName + r.firstName, r);
        }

        return results;
    }

    @Override
    public List<Demographic> getClientsByChartNo(String chartNo) {
        String queryStr = " FROM Demographic d where d.chartNo=?1";
        @SuppressWarnings("unchecked")
        List<Demographic> rs = (List<Demographic>) JpqlQueryHelper.find(entityManager(), queryStr, chartNo);

        if (log.isDebugEnabled()) {
            log.debug("getClientsByChartNo: # of results=" + rs.size());
        }

        return rs;
    }

    @Override
    public List<Demographic> getClientsByHealthCard(String num, String type) {
        String queryStr = " FROM Demographic d where d.hin=?1 and d.hcType=?2";
        @SuppressWarnings("unchecked")
        List<Demographic> rs = (List<Demographic>) JpqlQueryHelper.find(entityManager(), queryStr, num, type);

        if (log.isDebugEnabled()) {
            log.debug("getClientsByHealthCard: # of results=" + rs.size());
        }

        return rs;
    }

    @Override
    public List<Demographic> searchByHealthCard(String hin, String hcType) {
        return getClientsByHealthCard(hin, hcType);
    }

    // from DemographicData
    @Override
    public Demographic getDemographicByNamePhoneEmail(String firstName, String lastName, String hPhone, String wPhone,
                                                      String email) {

        List<String> params = new ArrayList<String>();
        StringBuilder whereClause = new StringBuilder();
        int paramIndex = 1;

        if (firstName.trim().length() > 0) {
            whereClause.append("firstName=?" + paramIndex++);
            params.add(firstName.trim());
        }
        if (lastName.trim().length() > 0) {
            if (params.size() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("lastName=?" + paramIndex++);
            params.add(lastName.trim());
        }
        if (hPhone.trim().length() > 0) {
            if (params.size() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("phone=?" + paramIndex++);
            params.add(hPhone.trim());
        }
        if (wPhone.trim().length() > 0) {
            if (params.size() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("phone2=?" + paramIndex++);
            params.add(wPhone.trim());
        }
        if (email.trim().length() > 0) {
            if (params.size() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("email=?" + paramIndex++);
            params.add(email.trim());
        }

        if (whereClause.length() == 0) {
            throw new IllegalArgumentException("you need to search by something");
        }
        String sql = "FROM Demographic WHERE " + whereClause;

        @SuppressWarnings("unchecked")
        List<Demographic> demographics = (List<Demographic>) JpqlQueryHelper.find(entityManager(), sql,
            (Object[]) params.toArray(new String[params.size()]));

        if (!demographics.isEmpty()) {
            return demographics.get(0);
        }

        return null;
    }

    @Override
    public List<Demographic> searchByHealthCard(String hin) {
        String queryStr = " FROM Demographic d where d.hin=?1";
        @SuppressWarnings("unchecked")
        List<Demographic> rs = (List<Demographic>) JpqlQueryHelper.find(entityManager(), queryStr, hin);

        return rs;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> getDemographicWithLastFirstDOB(String lastname, String firstname, String year_of_birth,
                                                            String month_of_birth, String date_of_birth) {
        List<String> params = new ArrayList<String>();
        StringBuilder sql = new StringBuilder("FROM Demographic WHERE lastName like ?1 and firstName like ?2");
        params.add(lastname + "%");
        params.add(firstname + "%");

        int paramIndex = 3;
        if (year_of_birth != null) {
            sql.append(" AND yearOfBirth = ?").append(paramIndex++);
            params.add(year_of_birth);
        }
        if (month_of_birth != null) {
            sql.append(" AND monthOfBirth = ?").append(paramIndex++);
            params.add(month_of_birth);
        }
        if (date_of_birth != null) {
            sql.append(" AND dateOfBirth = ?").append(paramIndex++);
            params.add(date_of_birth);
        }

        return (List<Demographic>) JpqlQueryHelper.find(entityManager(), sql.toString(), (Object[]) params.toArray(new String[params.size()]));
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> getDemographicWithLastFirstDOBExact(String lastname, String firstname,
                                                                 String year_of_birth, String month_of_birth, String date_of_birth) {
        List<String> params = new ArrayList<String>();
        StringBuilder sql = new StringBuilder("FROM Demographic WHERE lastName = ?1 and firstName = ?2");
        params.add(lastname);
        params.add(firstname);

        int paramIndex = 3;
        if (year_of_birth != null) {
            sql.append(" AND yearOfBirth = ?").append(paramIndex++);
            params.add(year_of_birth);
        }
        if (month_of_birth != null) {
            sql.append(" AND monthOfBirth = ?").append(paramIndex++);
            params.add(month_of_birth);
        }
        if (date_of_birth != null) {
            sql.append(" AND dateOfBirth = ?").append(paramIndex++);
            params.add(date_of_birth);
        }

        return (List<Demographic>) JpqlQueryHelper.find(entityManager(), sql.toString(), (Object[]) params.toArray(new String[params.size()]));
    }

    /**
     * Checks whether a demographic record exists with the given first and last name.
     *
     * @param firstName String the patient's first name (exact match)
     * @param lastName String the patient's last name (exact match)
     * @return boolean true if at least one matching record exists, false otherwise
     */
    @Override
    public boolean existsByFirstAndLastName(String firstName, String lastName) {
        String sql = "SELECT COUNT(*) FROM Demographic WHERE firstName = ?1 AND lastName = ?2";
        Long count = (Long) JpqlQueryHelper.find(entityManager(), sql, firstName, lastName).get(0);
        return count > 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> getDemographicsByHealthNum(String hin) {
        String sSQL = "from Demographic d where d.hin=?1";
        return (List<Demographic>) JpqlQueryHelper.find(entityManager(), sSQL, hin);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Integer> getActiveDemographicIds() {
        String sSQL = "select d.demographicNo from Demographic d where d.patientStatus=?1";
        return (List<Integer>) JpqlQueryHelper.find(entityManager(), sSQL, "AC");
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Integer> getDemographicIds() {
        String sSQL = "select d.demographicNo from Demographic d";
        return (List<Integer>) JpqlQueryHelper.find(entityManager(), sSQL);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> getDemographicWithGreaterThanYearOfBirth(int yearOfBirth) {
        String sSQL = "from Demographic d where d.yearOfBirth > ?1";
        return (List<Demographic>) JpqlQueryHelper.find(entityManager(), sSQL, String.valueOf(yearOfBirth));
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> search_catchment(String rosterStatus, int offset, int limit) {
        String sql = "from Demographic d where d.rosterStatus=:status and (d.postal not like 'L0R%' and d.postal not like 'L3M%' and d.postal not like 'L8E%' and d.postal not like 'L9A%' and d.postal not like 'L8G%' and d.postal not like 'L9B%' and d.postal not like 'L8H%' and d.postal not like 'L9C%' and d.postal not like 'L8J%' and d.postal not like 'L9G%' and d.postal not like 'L8K%' and d.postal not like 'L9H%' and d.postal not like 'L8L%' and d.postal not like 'L9K%' and d.postal not like 'L8M%' and d.postal not like 'L8N%' and d.postal not like 'N0A%' and d.postal not like 'L8P%' and d.postal not like 'N3W%' and d.postal not like 'L8R%' and d.postal not like 'L8S%' and d.postal not like 'L8T%' and d.postal not like 'L8V%' and d.postal not like 'L8W%' and d.postal not like 'K8R%' and d.postal not like 'L0R%' and d.postal not like 'L5P%' and d.postal not like 'L8A%' and d.postal not like 'L8B%' and d.postal not like 'L8C%' and d.postal not like 'L8L%' and d.postal not like 'L9L%' and d.postal not like 'L9N%' and d.postal not like 'L9S%' and d.postal not like 'M9C%' and d.postal not like 'N0B%1L0' and d.postal not like 'L7L%' and d.postal not like 'L7M%' and d.postal not like 'L7N%' and d.postal not like 'L7P%' and d.postal not like 'L7R%' and d.postal not like 'L7S%' and d.postal not like 'L7T%' )";
        EntityManager s = entityManager();

            Query q = s.createQuery(sql);
            q.setParameter("status", rosterStatus);
            q.setMaxResults(limit);
            q.setFirstResult(offset);
            return q.getResultList();
    }

    /** Allowlisted HQL field names for {@link #findByField}. */
    private static final java.util.Set<String> VALID_FIND_BY_FIELDS = java.util.Set.of(
        "DemographicNo", "LastName", "FirstName", "ChartNo", "Sex", "YearOfBirth", "PatientStatus"
    );

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> findByField(String fieldName, Object fieldValue, String orderBy, int offset) {
        boolean isFieldValueEmpty = fieldValue == null || fieldValue.equals("");

        // Validate fieldName against allowlist to prevent HQL injection
        if (fieldName != null && !VALID_FIND_BY_FIELDS.contains(fieldName)) {
            fieldName = "LastName";
        }

        String sql = "FROM Demographic d WHERE d." + fieldName + " LIKE :fieldValue";
        if (isFieldValueEmpty) {
            sql = "FROM Demographic d";
        }

        if (orderBy != null && !orderBy.isEmpty()) {
            // Validate orderBy against allowlist to prevent HQL injection
            if (!VALID_FIND_BY_FIELDS.contains(orderBy)) {
                orderBy = "LastName";
            }
            sql = sql + " ORDER BY d." + orderBy;
        }

        EntityManager s = entityManager();
        Query q = s.createQuery(sql); // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string, java.lang.security.audit.sqli.jpa-sqli.jpa-sqli -- field/order names are allowlisted above; fieldValue is bound via setParameter below
        if (!isFieldValueEmpty) {
            q.setParameter("fieldValue", fieldValue);
        }

        q.setMaxResults(10);

        if (offset > 0) {
            q.setFirstResult(offset);
        }
        return q.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Demographic> findByCriterion(DemographicCriterion c) {
        if (c.getHealthNumber() == null || c.getHealthNumber().trim().isEmpty()) {
            String sSQL = "FROM Demographic d " + "WHERE d.lastName like ?1 " + "AND d.firstName like ?2 "
                + "AND d.yearOfBirth = ?3 " + "AND d.monthOfBirth = ?4 " + "AND d.dateOfBirth = ?5 "
                + "AND d.sex like ?6 " + "AND d.patientStatus = ?7";
            return (List<Demographic>) JpqlQueryHelper.find(entityManager(), sSQL, c.getAll(false));
        }

        String sSQL = "FROM Demographic d " + "WHERE d.hin = ?1 " + "AND d.lastName like ?2 " + "AND d.firstName like ?3 "
            + "AND d.yearOfBirth = ?4 " + "AND d.monthOfBirth = ?5 " + "AND d.dateOfBirth = ?6 "
            + "AND d.sex like ?7 " + "AND d.patientStatus = ?8";
        return (List<Demographic>) JpqlQueryHelper.find(entityManager(), sSQL, c.getAll(true));
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Object[]> findDemographicsForFluReport(String providerNo) {
        String sql = "select demographic_no, CONCAT(last_name,',',first_name) as demoname, phone, roster_status, patient_status, "
            + "DATE_FORMAT(CONCAT((year_of_birth), '-', (month_of_birth), '-',(date_of_birth)),'%Y-%m-%d') as dob, "
            + "(YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((year_of_birth), '-', (month_of_birth),'-',(date_of_birth)),'%Y-%m-%d')))-"
            + "(RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((year_of_birth), '-', (month_of_birth),'-',(date_of_birth)),'%Y-%m-%d'),5)) as age "
            + "from demographic  where (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((year_of_birth),'-', (month_of_birth),'-',(date_of_birth)),'%Y-%m-%d')))-"
            + "(RIGHT(CURRENT_DATE,5)<"
            + "RIGHT(DATE_FORMAT(CONCAT((year_of_birth), '-', (month_of_birth),'-',(date_of_birth)),'%Y-%m-%d'),5)) >= 65 "
            + "and (patient_status = 'AC' or patient_status = 'UHIP') "
            + "and (roster_status='RO' or roster_status='NR' or roster_status='FS' or roster_status='RF' or roster_status='PL')";
        if (providerNo != null && !providerNo.equals("-1")) {
            sql = sql + " and provider_no = :providerNo ";
        }
        sql = sql + " order by last_name ";

        EntityManager session = entityManager();
            Query sqlQuery = session.createNativeQuery(sql);
            if (providerNo != null && !providerNo.equals("-1")) {
                sqlQuery.setParameter("providerNo", providerNo);
            }
            return sqlQuery.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Integer> getActiveDemographicIdsOlderThan(int age) {
        List<Integer> ids = new ArrayList<Integer>();
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, Integer.parseInt(String.valueOf("-" + (age + 1))));

        List<Object[]> demographics = (List<Object[]>) JpqlQueryHelper.find(entityManager(),
            "SELECT d.demographicNo,d.yearOfBirth,d.monthOfBirth,d.dateOfBirth FROM Demographic d WHERE d.patientStatus = 'AC'");
        for (Object[] tm : demographics) {
            Demographic d = new Demographic();
            d.setDemographicNo((Integer) tm[0]);
            d.setYearOfBirth((String) tm[1]);
            d.setMonthOfBirth((String) tm[2]);
            d.setDateOfBirth((String) tm[3]);

            if (Integer.parseInt(d.getAge()) > 55) {
                ids.add(d.getDemographicNo());
            }
        }
        return ids;
    }

    public static class DemographicCriterion {

        private String healthNumber;
        private String lastNamePrefix;
        private String firstNamePrefix;
        private String birthYear;
        private String birthMonth;
        private String birthDay;
        private String sex;
        private String patientStatus;

        public DemographicCriterion() {
            this("", "", "", "", "", "", "", "");
        }

        public DemographicCriterion(String healthNumber, String lastNamePrefix, String firstNamePrefix,
                                    String birthYear, String birthMonth, String birthDay, String sex, String patientStatus) {
            super();
            this.healthNumber = healthNumber;
            this.lastNamePrefix = lastNamePrefix;
            this.firstNamePrefix = firstNamePrefix;
            this.birthYear = birthYear;
            this.birthMonth = birthMonth;
            this.birthDay = birthDay;
            this.sex = sex;
            this.patientStatus = patientStatus;
        }

        Object[] getAll(boolean includeHin) {
            if (includeHin) {
                return new Object[]{healthNumber, lastNamePrefix + "%", firstNamePrefix + "%", birthYear, birthMonth,
                    birthDay, sex.toUpperCase() + "%", patientStatus};
            } else {
                return new Object[]{lastNamePrefix + "%", firstNamePrefix + "%", birthYear, birthMonth, birthDay,
                    sex.toUpperCase() + "%", patientStatus};
            }
        }

        public String getHealthNumber() {
            return healthNumber;
        }

        public void setHealthNumber(String healthNumber) {
            this.healthNumber = healthNumber;
        }

        public String getLastNamePrefix() {
            return lastNamePrefix;
        }

        public void setLastNamePrefix(String lastNamePrefix) {
            this.lastNamePrefix = lastNamePrefix;
        }

        public String getFirstNamePrefix() {
            return firstNamePrefix;
        }

        public void setFirstNamePrefix(String firstNamePrefix) {
            this.firstNamePrefix = firstNamePrefix;
        }

        public String getBirthYear() {
            return birthYear;
        }

        public void setBirthYear(String birthYear) {
            this.birthYear = birthYear;
        }

        public String getBirthMonth() {
            return birthMonth;
        }

        public void setBirthMonth(String birthMonth) {
            this.birthMonth = birthMonth;
        }

        public String getBirthDay() {
            return birthDay;
        }

        public void setBirthDay(String birthDay) {
            this.birthDay = birthDay;
        }

        public String getSex() {
            return sex;
        }

        public void setSex(String sex) {
            this.sex = sex;
        }

        public String getPatientStatus() {
            return patientStatus;
        }

        public void setPatientStatus(String patientStatus) {
            this.patientStatus = patientStatus;
        }
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * This is a hack function because we store dateOfBirth
     * in the DB as 3 string fields and where each string
     * is 0 padded like 05. So this will convert to that
     * format for us. We really need to get rid of this
     * date anomaly soon.
     */
    private static String ensure2DigitDateHack(int i) {
        if (i >= 10)
            return (String.valueOf(i));
        else
            return ("0" + i);
    }

    @Override
    public List<Integer> getDemographicIdsAddedSince(Date value) {
        String sSQL = "select d.demographicNo from Demographic d where d.lastUpdateDate >?1";
        return (List<Integer>) JpqlQueryHelper.find(entityManager(), sSQL, value);
    }

    protected final void setLimit(Query query, int itemsToReturn) {
        if (itemsToReturn > MAX_SELECT_SIZE)
            throw (new IllegalArgumentException("Requested too large of a result list size : " + itemsToReturn));

        query.setMaxResults(itemsToReturn);
    }

    @Override
    public List<Demographic> getDemographicByRosterStatus(String rosterStatus, String patientStatus) {
        if (StringUtils.isEmpty(patientStatus)) {
            patientStatus = "AC";
        }
        String queryStr = " FROM Demographic d where d.rosterStatus=?1 and d.patientStatus = ?2";
        Object[] params = new Object[]{rosterStatus, patientStatus};
        @SuppressWarnings("unchecked")
        List<Demographic> rs = (List<Demographic>) JpqlQueryHelper.find(entityManager(), queryStr, params);

        return rs;
    }

    @Override
    public Integer searchPatientCount(LoggedInInfo loggedInInfo, DemographicSearchRequest searchRequest) {
        Map<String, Object> params = new HashMap<String, Object>();

        String demographicQuery = generateDemographicSearchQuery(loggedInInfo, searchRequest, params, "count(*)");

        MiscUtils.getLogger().debug("demographicQuery: {}", LogSafe.sanitize(demographicQuery, 1000));

        EntityManager session = entityManager();
            Query sqlQuery = session.createNativeQuery(demographicQuery); // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string -- generateDemographicSearchQuery builds SQL from enum-selected column/order fragments plus a server-owned inactive_statuses config list; all request values flow through bound params (setParameter below)
            for (String key : params.keySet()) {
                sqlQuery.setParameter(key, params.get(key));
                MiscUtils.getLogger().debug("query param: {}={}", LogSafe.sanitize(key),
                    PHI_PARAM_KEYS.contains(key) ? "[REDACTED]" : LogSafe.sanitize(String.valueOf(params.get(key))));
            }
            Integer result = ((Number) sqlQuery.getSingleResult()).intValue();
            return result;
    }

    @Override
    public List<DemographicSearchResult> searchPatients(LoggedInInfo loggedInInfo,
                                                        DemographicSearchRequest searchRequest, int startIndex, int itemsToReturn) {
        Map<String, Object> params = new HashMap<String, Object>();

        String demographicQuery = generateDemographicSearchQuery(loggedInInfo, searchRequest, params,
            "d.demographic_no, d.last_name, d.first_name, d.chart_no, d.sex, d.provider_no, d.roster_status," +
                " d.patient_status, d.phone, d.year_of_birth,d.month_of_birth,d.date_of_birth,p.last_name as providerLastName,"
                +
                "p.first_name as providerFirstName,d.hin,dm.merged_to");

        EntityManager session = entityManager();
        NativeQuery<?> baseQuery = session.createNativeQuery(demographicQuery).unwrap(NativeQuery.class); // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string -- generateDemographicSearchQuery builds SQL from enum-selected column/order fragments plus a server-owned inactive_statuses config list; all request values flow through bound params (setParameter below)

        for (String key : params.keySet()) {
            baseQuery.setParameter(key, params.get(key));
        }

        baseQuery.setFirstResult(startIndex);
        // Replace the Hibernate 5 setResultTransformer(ResultTransformer) API —
        // removed in Hibernate 6 — with setTupleTransformer() backed by the
        // existing DemographicSearchResultTransformer.transformTuple() logic.
        // setTupleTransformer returns a typed NativeQuery<R> that the compiler can
        // use to track the row element type through getResultList().
        DemographicSearchResultTransformer transformer = new DemographicSearchResultTransformer();
        transformer.setDemographicDao(this);
        NativeQuery<DemographicSearchResult> sqlQuery = baseQuery.setTupleTransformer(
            (tuple, aliases) -> (DemographicSearchResult) transformer.transformTuple(tuple, aliases));
        setLimit(sqlQuery, itemsToReturn);

        return sqlQuery.getResultList();
    }

    private String generateDemographicSearchQuery(LoggedInInfo loggedInInfo, DemographicSearchRequest searchRequest,
                                                  Map<String, Object> params, String select) {
        CarlosProperties props = CarlosProperties.getInstance();

        params.put("keyword", searchRequest.getKeyword());

        String fieldname = "";
        String regularexp = "regexp";

        if (searchRequest.getKeyword().indexOf("*") != -1 || searchRequest.getKeyword().indexOf("%") != -1) {
            regularexp = "like";
        }

        if (searchRequest.getMode() == SEARCHMODE.Address) {
            fieldname = "d.address";
        }
        if (searchRequest.getMode() == SEARCHMODE.Phone) {
            fieldname = "d.phone";
        }
        if (searchRequest.getMode() == SEARCHMODE.DemographicNo) {
            fieldname = "d.demographic_no";
        }

        if (searchRequest.getMode() == SEARCHMODE.HIN) {
            fieldname = "d.hin";
        }
        if (searchRequest.getMode() == SEARCHMODE.DOB) {
            fieldname = "d.year_of_birth = :year and d.month_of_birth = :month and d.date_of_birth ";

            try {
                String year = searchRequest.getKeyword().substring(0, 4);
                String month = searchRequest.getKeyword().substring(5, 7);
                String day = searchRequest.getKeyword().substring(8);

                params.put("year", year);
                params.put("month", month);
                params.put("keyword", day);

                // Validate the date parts
                new GregorianCalendar(Integer.parseInt(year), Integer.parseInt(month) - 1,
                    Integer.parseInt(day));
            } catch (Exception e) {
                // this is okay, person inputed a bad date, we'll ignore for now
                params.put("year", null);
                params.put("month", null);
                params.put("keyword", null);
            }
        }
        if (searchRequest.getMode() == SEARCHMODE.ChartNo) {
            fieldname = "d.chart_no";
        }
        if (searchRequest.getMode() == SEARCHMODE.HIN) {
            fieldname = "d.hin";
        }

        if (searchRequest.getMode() == SEARCHMODE.Name) {
            if (searchRequest.getKeyword().indexOf(",") == -1) {
                fieldname = "lower(d.last_name)";
            } else if (searchRequest.getKeyword().indexOf(",") == (searchRequest.getKeyword().length() - 1)) {
                fieldname = "lower(d.last_name)";
                params.put("keyword",
                    searchRequest.getKeyword().substring(0, searchRequest.getKeyword().length() - 1).trim());
            } else if (searchRequest.getKeyword().indexOf(",") == 0) {
                fieldname = "lower(d.first_name)";
                params.put("keyword", searchRequest.getKeyword().substring(1).trim());
            } else {
                params.put("extraKeyword", searchRequest.getKeyword().split(",")[0].trim());
                params.put("keyword", searchRequest.getKeyword().split(",")[1].trim());
                fieldname = "lower(d.last_name) " + regularexp + " :extraKeyword" + " and lower(d.first_name) ";
            }
        }

        String ptstatusexp = "";

        if (searchRequest.isActive()) {
            ptstatusexp = " and d.patient_status not in ("
                + props.getProperty("inactive_statuses", "'IN','DE','IC', 'ID', 'MO', 'FI'") + ") ";
        } else {
            ptstatusexp = " and d.patient_status in ("
                + props.getProperty("inactive_statuses", "'IN','DE','IC', 'ID', 'MO', 'FI'") + ") ";
        }

        String domainRestriction = "";
        if (!searchRequest.isOutOfDomain()) {
            domainRestriction = " and d.demographic_no in ( select distinct a.client_id from program_provider pp,admission a WHERE pp.program_id=a.program_id AND pp.provider_no=:providerNo ) ";
            params.put("providerNo", loggedInInfo.getLoggedInProviderNo());
        }

        String orderBy = "d.last_name,d.first_name";

        String orderDir = "asc";
        if (searchRequest.getSortDir() != null) {
            orderDir = searchRequest.getSortDir().toString();
        }
        if (SORTMODE.Address.equals(searchRequest.getSortMode())) {
            orderBy = "d.address " + orderDir;
        } else if (SORTMODE.ChartNo.equals(searchRequest.getSortMode())) {
            orderBy = "d.chart_no " + orderDir;
        } else if (SORTMODE.DemographicNo.equals(searchRequest.getSortMode())) {
            orderBy = "d.demographic_no " + orderDir;
        } else if (SORTMODE.DOB.equals(searchRequest.getSortMode())) {
            orderBy = "year_of_birth " + orderDir + ",month_of_birth " + orderDir + ",date_of_birth " + orderDir;
        } else if (SORTMODE.Name.equals(searchRequest.getSortMode())) {
            orderBy = "d.last_name " + orderDir + ",d.first_name " + orderDir;
        } else if (SORTMODE.Phone.equals(searchRequest.getSortMode())) {
            orderBy = "d.phone " + orderDir;
        } else if (SORTMODE.ProviderName.equals(searchRequest.getSortMode())) {
            orderBy = "p.last_name " + orderDir + ",p.first_name " + orderDir;
        } else if (SORTMODE.PS.equals(searchRequest.getSortMode())) {
            orderBy = "d.patient_status " + orderDir;
        } else if (SORTMODE.RS.equals(searchRequest.getSortMode())) {
            orderBy = "d.roster_status " + orderDir;
        } else if (SORTMODE.Sex.equals(searchRequest.getSortMode())) {
            orderBy = "d.sex " + orderDir;
        }

        orderBy = " ORDER BY " + orderBy;
        return "select " + select
            + " from demographic d left join provider p on d.provider_no = p.provider_no left join demographic_merged dm on d.demographic_no = dm.demographic_no where "
            + fieldname + " " + regularexp + " :keyword " + ptstatusexp + domainRestriction + orderBy;
    }

    @Override
    public List<Demographic> getDemographics(List<Integer> demographicIds) {
        if (demographicIds.size() == 0)
            return (new ArrayList<Demographic>());
        if (demographicIds.size() > MAX_SELECT_SIZE)
            throw (new IllegalArgumentException("please chunk your requests to max : " + MAX_SELECT_SIZE));

        String q = "FROM Demographic d WHERE d.demographicNo in (:ids)";
        @SuppressWarnings("unchecked")
        Map<String, Object> namedParams = new HashMap<>();
        namedParams.put("ids", demographicIds);
        List<Demographic> results = (List<Demographic>) JpqlQueryHelper.find(entityManager(), q, namedParams);
        return (results);

    }


    @SuppressWarnings("unchecked")
    @Override
    public List<Integer> getMissingExtKey(String keyName) {
        EntityManager session = entityManager();
            Query sqlQuery = session.createNativeQuery(
                "select distinct d.demographic_no from demographic d where d.demographic_no not in (select distinct d.demographic_no from demographic d, demographicExt e where d.demographic_no = e.demographic_no and key_val=:key)");
            sqlQuery.setParameter("key", keyName);
            List<Integer> ids = sqlQuery.getResultList();

            return ids;

    }


    @Override
    public List<Demographic> getActiveDemographicAfter(Date afterDatetimeExclusive) {
        String q = "From Demographic d where d.patientStatus='AC'";
        if (afterDatetimeExclusive != null) {
            q += " and d.lastUpdateDate > ?1";
        }

        List<Demographic> rs = null;
        rs = afterDatetimeExclusive != null ? (List<Demographic>) JpqlQueryHelper.find(entityManager(), q, afterDatetimeExclusive)
            : (List<Demographic>) JpqlQueryHelper.find(entityManager(), q);

        return rs;
    }

    @Override
    public List<Demographic> findByLastNameAndDob(String lastName, Calendar dateOfBirth) {
        return findByAttributes(null, null, lastName, null, dateOfBirth, null, null, null, null, null, 0, 99);
    }

    @Override
    public List<Demographic> findByFirstAndLastName(String name, String start, String end) {
        String[] nameArray = name.split(",");
        String firstName = name + "%";
        String lastName = name + "%";
        int startIndex = Integer.parseInt(start);

        if (nameArray.length > 1) {
            firstName = nameArray[1].trim() + "%";
            lastName = nameArray[0].trim() + "%";
        }

        return findByAttributes(
            null,
            firstName,
            lastName,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            startIndex,
            Integer.parseInt(end) - startIndex);
    }

    @Override
    public List<Demographic> findByDob(Calendar dateOfBirth, String start, int numToReturn) {
        int startIndex = Integer.parseInt(start);
        return findByAttributes(
            null,
            null,
            null,
            null,
            dateOfBirth,
            null,
            null,
            null,
            null,
            null,
            startIndex,
            numToReturn,
            true);
    }

    @Override
    public List<Demographic> findByPhone(String phone, String start, int numToReturn) {
        int startIndex = Integer.parseInt(start);
        return findByAttributes(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            phone,
            null,
            null,
            startIndex,
            numToReturn,
            true);
    }

    @Override
    public List<Demographic> findByHin(String hin, String start, int numToReturn) {
        int startIndex = Integer.parseInt(start);
        return findByAttributes(
            hin,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            startIndex,
            numToReturn,
            true);
    }

    // --- DTO projection methods ---

    /**
     * Returns a lightweight header projection for a single demographic, including
     * the most responsible provider's name via a LEFT JOIN.
     *
     * @param demographicNo Integer the demographic ID to retrieve
     * @return DemographicHeaderDTO the header projection, or {@code null} if not found or demographicNo is null
     * @since 2026-04-11
     */
    @Override
    public DemographicHeaderDTO getDemographicHeader(Integer demographicNo) {
        if (demographicNo == null) {
            return null;
        }
        jakarta.persistence.TypedQuery<DemographicHeaderDTO> query = entityManager().createQuery(
                "SELECT NEW io.github.carlos_emr.carlos.demographic.dto.DemographicHeaderDTO(d.demographicNo, d.lastName, d.firstName, d.sex, d.sexDesc, d.yearOfBirth, d.monthOfBirth, d.dateOfBirth, d.hin, d.ver, d.hcType, d.chartNo, d.patientStatus, d.rosterStatus, d.providerNo, p.lastName, p.firstName) FROM Demographic d LEFT JOIN Provider p ON p.providerNo = d.providerNo WHERE d.demographicNo = :demoNo",
                DemographicHeaderDTO.class);
        query.setParameter("demoNo", demographicNo);
        query.setMaxResults(1);
        List<DemographicHeaderDTO> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Searches demographics by name and returns lightweight list item projections.
     * Supports "lastName" or "lastName,firstName" format. Results are ordered by
     * last name then first name ascending.
     *
     * @param searchString String the search string in "lastName" or "lastName,firstName" format
     * @param limit int maximum number of results to return
     * @param offset int starting position for pagination
     * @param providerNo String the provider number for program domain restriction (can be null to skip)
     * @param outOfDomain boolean if true, skip program domain restriction even when providerNo is set
     * @return List of DemographicListItemDTO matching demographics, ordered by name
     * @since 2026-04-11
     */
    @Override
    public List<DemographicListItemDTO> searchDemographicDTOByName(String searchString, int limit, int offset,
                                                                    String providerNo, boolean outOfDomain) {
        String baseQuery = "SELECT NEW io.github.carlos_emr.carlos.demographic.dto.DemographicListItemDTO(d.demographicNo, d.lastName, d.firstName, d.alias, d.sex, d.yearOfBirth, d.monthOfBirth, d.dateOfBirth, d.patientStatus, d.rosterStatus, d.providerNo, d.chartNo, d.phone, d.email, d.hin, d.address) FROM Demographic d WHERE d.lastName like :lastName";
        String[] name = Objects.requireNonNullElse(searchString, "").split(",");
        boolean hasFirstName = name.length == 2;

        if (hasFirstName) {
            baseQuery = baseQuery.concat(" and (d.firstName like :firstName or d.alias like :firstName)");
        }
        if (providerNo != null && !outOfDomain) {
            baseQuery = baseQuery.concat(" AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ")");
        }
        baseQuery = baseQuery.concat(" ORDER BY d.lastName ASC, d.firstName ASC");

        jakarta.persistence.TypedQuery<DemographicListItemDTO> query = entityManager().createQuery(baseQuery, DemographicListItemDTO.class);
        query.setParameter("lastName", name[0].trim() + "%");
        if (hasFirstName) {
            query.setParameter("firstName", name[1].trim() + "%");
        }
        if (providerNo != null && !outOfDomain) {
            query.setParameter("providerNo", providerNo);
        }
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

}
