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

import java.util.Objects;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
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

import javax.persistence.PersistenceException;

import ca.openosp.Misc;
import ca.openosp.openo.utils.Utility;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Expression;
import org.hibernate.criterion.LogicalExpression;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.PMmodule.web.formbean.ClientListsReportFormBean;
import ca.openosp.openo.PMmodule.web.formbean.ClientSearchFormBean;
import ca.openosp.openo.caisi_integrator.ws.MatchingDemographicParameters;
import ca.openosp.openo.commn.DemographicSearchResultTransformer;
import ca.openosp.openo.commn.Gender;
import ca.openosp.openo.commn.NativeSql;
import ca.openosp.openo.commn.model.Admission;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.DemographicExt;
import ca.openosp.openo.event.DemographicCreateEvent;
import ca.openosp.openo.event.DemographicUpdateEvent;
import ca.openosp.openo.integration.hl7.generators.HL7A04Generator;
import ca.openosp.openo.utility.DbConnectionFilter;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchRequest;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchRequest.SEARCHMODE;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchRequest.SORTMODE;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.orm.hibernate5.HibernateCallback;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import ca.openosp.MyDateFormat;
import ca.openosp.OscarProperties;
import ca.openosp.openo.util.SqlUtils;

/**
 *
 */
@Transactional
public class DemographicDaoImpl extends HibernateDaoSupport implements ApplicationEventPublisherAware, DemographicDao {

    private static final int MAX_SELECT_SIZE = 500;

    static Logger log = MiscUtils.getLogger();

    private ApplicationEventPublisher publisher;
    // public SessionFactory sessionFactory;

    @Autowired
    public void setSessionFactoryOverride(SessionFactory sessionFactory) {
        super.setSessionFactory(sessionFactory);
    }

    /**
     * Finds merged demographic IDs for the specified demographic.
     *
     * @param demographicNo Demographic ID to find merged records for
     * @return Returns the list of merged (child ids) or empty list if the record is
     * not merged to any other record
     */
    @SuppressWarnings("unchecked")
    @NativeSql("demographic_merged")
    @Override
    public List<Integer> getMergedDemographics(Integer demographicNo) {
        // Please don't tell me anything about session handling - this hibernate stuff
        // must be refactored into JPA, then we will talk, ok?
        // Session session = getSession();
        Session session = currentSession();
        try {
            SQLQuery sqlQuery = session.createSQLQuery(
                "select demographic_no from demographic_merged where merged_to = :parentId and deleted = 0");
            sqlQuery.setInteger("parentId", demographicNo);
            return sqlQuery.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
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

        return this.getHibernateTemplate().get(Demographic.class, dNo);
    }

    // ADD BY PINE-SOFT
    @Override
    public List getDemographics() {
        logger.error(
            "No one should be calling this method, this is a good way to run out of memory and crash a server... this is too large of a result set, it should be pagenated.",
            new IllegalArgumentException("The entire demographic table is too big to allow a full select."));
        return this.getHibernateTemplate().find("from Demographic d order by d.LastName");
    }

    @Override
    public Long getActiveDemographicCount() {
        List<?> res = this.getHibernateTemplate()
            .find("SELECT COUNT(*) FROM Demographic d WHERE d.PatientStatus = 'AC'");
        for (Object r : res) {
            return (Long) r;
        }
        return 0L;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Demographic> getActiveDemographics(final int offset, final int limit) {
        return (List<Demographic>) getHibernateTemplate().execute(new HibernateCallback<List<Demographic>>() {
            @Override
            public List<Demographic> doInHibernate(Session session) throws HibernateException {
                Query query = session.createQuery("FROM Demographic d WHERE d.PatientStatus = 'AC'");
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

                return query.list();
            }
        });
    }

    @Override
    public Demographic getDemographicById(Integer demographic_id) {
        String q = "FROM Demographic d WHERE d.DemographicNo = ?0";
        List rs = getHibernateTemplate().find(q, demographic_id);

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
        String q = "From Demographic d where d.ProviderNo = ?0 ";
        if (onlyActive) {
            q = "From Demographic d where d.ProviderNo = ?0 and d.PatientStatus = 'AC' ";
        }
        List<Demographic> rs = (List<Demographic>) getHibernateTemplate().find(q, new Object[]{providerNo});
        return rs;
    }

    @Override
    public List<Integer> getDemographicNosByProvider(String providerNo, boolean onlyActive) {
        String q = "From Demographic d where d.ProviderNo = ?0 ";
        if (onlyActive) {
            q = "Select d.DemographicNo From Demographic d where d.ProviderNo = ?0  ";
        }
        List<Integer> rs = (List<Integer>) getHibernateTemplate().find(q, new Object[]{providerNo});
        return rs;
    }


    /*
     * get demographics according to their program, admit time, discharge time,
     * ordered by lastname and first name
     */
    @Override
    public List getActiveDemographicByProgram(int programId, Date dt, Date defdt) {
        // get duplicated clients from this sql
        String q = "Select d From Demographic d, Admission a "
            + "Where (d.PatientStatus=?0 or d.PatientStatus='' or d.PatientStatus=null) and d.DemographicNo=a.clientId and a.programId=?1 and a.admissionDate<=?2 and "
            + "(a.dischargeDate>=?3 or (a.dischargeDate is null) or a.dischargeDate=?4)"
            + " order by d.LastName,d.FirstName";

        String status = "AC"; // only show active clients
        List rs = getHibernateTemplate().find(q, new Object[]{status, Integer.valueOf(programId), dt, dt, defdt});

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

    @Override
    public List<Demographic> getActiveDemosByHealthCardNo(String hcn, String hcnType) {

        // Session s = getSession();
        Session s = currentSession();
        try {
            List rs = s.createCriteria(Demographic.class).add(Expression.eq("Hin", hcn))
                .add(Expression.eq("HcType", hcnType)).add(Expression.eq("PatientStatus", "AC")).list();
            return rs;
        } finally {
            // releaseSession(s);
            //s.close();
        }
    }

    @Override
    public Set getArchiveDemographicByProgramOptimized(int programId, Date dt, Date defdt) {
        Set<Demographic> archivedClients = new java.util.LinkedHashSet<Demographic>();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sqlQuery = "select distinct d.demographic_no,d.first_name,d.last_name,(select count(*) from admission a where client_id=d.demographic_no and admission_status='current' and program_id="
            + programId + " and admission_date<='" + sdf.format(dt)
            + "') as is_active from admission a,demographic d where a.client_id=d.demographic_no and (d.patient_status='AC' or d.patient_status='' or d.patient_status=null) and program_id="
            + programId
            + " and (d.anonymous is null or d.anonymous != 'one-time-anonymous') ORDER BY d.last_name,d.first_name";
        // Session session = this.getSession();
        Session session = currentSession();

        SQLQuery q = session.createSQLQuery(sqlQuery);
        q.addScalar("d.demographic_no");
        q.addScalar("d.first_name");
        q.addScalar("d.last_name");
        q.addScalar("is_active");
        List results = q.list();

        Iterator iter = results.iterator();
        while (iter.hasNext()) {
            Object[] result = (Object[]) iter.next();
            if (((BigInteger) result[3]).intValue() == 0) {
                Demographic d = new Demographic();
                d.setDemographicNo((Integer) result[0]);
                d.setFirstName((String) result[1]);
                d.setLastName((String) result[2]);
                archivedClients.add(d);
            }
        }

        // this.releaseSession(session);
        //session.close();
        return archivedClients;

    }

    @Override
    public List getProgramIdByDemoNo(Integer demoNo) {
        String q = "Select a.programId From Admission a " + "Where a.clientId=?0 and a.admissionDate<=?1 and "
            + "(a.dischargeDate>=?2 or (a.dischargeDate is null) or a.dischargeDate=?3)";

        /* default time is Oscar default null time 0001-01-01. */
        Date defdt = new GregorianCalendar(1, 0, 1).getTime();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        Date dt = cal.getTime();

        List rs = getHibernateTemplate().find(q, new Object[]{demoNo, dt, dt, defdt});
        return rs;
    }

    @Override
    public void clear() {
        getHibernateTemplate().clear();

    }

    @Override
    public List getDemoProgram(Integer demoNo) {
        String q = "Select a.programId From Admission a Where a.clientId=?0";
        List rs = getHibernateTemplate().find(q, new Object[]{demoNo});
        return rs;
    }

    @Override
    public List getDemoProgramCurrent(Integer demoNo) {
        String q = "Select a.programId From Admission a Where a.clientId=?0 and a.admissionStatus='current'";
        List rs = getHibernateTemplate().find(q, new Object[]{demoNo});
        return rs;
    }

    public List<Integer> getDemographicIdsAdmittedIntoFacility(int facilityId) {
        Connection c = null;
        try {
            c = DbConnectionFilter.getThreadLocalDbConnection();
            PreparedStatement ps = c.prepareStatement(
                "select distinct(admission.client_id) from admission,program,Facility where admission.program_id=program.id and program.facilityId=?");
            ps.setInt(1, facilityId);
            ResultSet rs = ps.executeQuery();
            ArrayList<Integer> results = new ArrayList<Integer>();
            while (rs.next())
                results.add(rs.getInt(1));
            return (results);
        } catch (SQLException e) {
            throw (new PersistenceException(e));
        } finally {
            SqlUtils.closeResources(c, null, null);
        }
    }

    @Override
    public List<Demographic> searchDemographic(String searchStr) {
        String hql = "From Demographic d where ";
        List<String> params = new ArrayList<>();

        String[] parts = searchStr.split(",");
        if (searchStr.indexOf(",") != -1 && searchStr.trim().indexOf(",") != (searchStr.trim().length() - 1)) {
            hql += "last_name like ?0 and first_name like ?1";
            params.add(parts[0].trim() + "%");
            params.add(parts[1].trim() + "%");
        } else {
            hql += "last_name like ?0";
            params.add(parts[0].trim() + "%");
        }

        Object[] object = null;
        if (parts.length > 1) {
            object = new Object[]{parts[0].trim() + "%", parts[1].trim() + "%"};
        } else {
            object = new Object[]{parts[0].trim() + "%"};
        }
        List list = getHibernateTemplate().find(hql, object);
        return list;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchDemographicByNameString(String searchString, int startIndex, int itemsToReturn) {
        String sqlCommand = "select x from Demographic x";
        // Session session = this.getSession();
        Session session = currentSession();
        String ln = "";
        String fn = "";
        String where = "";
        try {
            if (searchString != null && searchString.length() > 0) {
                String[] sh = searchString.split(",");
                if (sh.length > 1) {
                    if (sh[0] != null && sh[0].trim().length() > 0) {
                        where = " x.LastName like :ln ";
                        ln = sh[0].trim();
                    }
                    if (sh[1] != null && sh[1].trim().length() > 0) {
                        if (where.length() > 0)
                            where = where + " and ";
                        where = where + " x.FirstName like :fn ";
                        fn = sh[1].trim();
                    }
                } else {
                    if (sh[0] != null && sh[0].trim().length() > 0) {
                        where = " x.LastName like :ln ";
                        ln = sh[0].trim();
                    }
                }
                if (where.length() > 0)
                    sqlCommand = sqlCommand + " where " + where;
            }
            Query q = session.createQuery(sqlCommand);
            if (ln.length() > 0)
                q.setParameter("ln", ln + "%");
            if (fn.length() > 0)
                q.setParameter("fn", fn + "%");
            q.setFirstResult(startIndex);
            q.setMaxResults(itemsToReturn);
            return (q.list());
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
    }

    private final String PROGRAM_DOMAIN_RESTRICTION = "select distinct a.clientId from ProgramProvider pp,Admission a WHERE pp.ProgramId=a.programId AND pp.ProviderNo=:providerNo";

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

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchDemographicByNameAndStatus(String searchStr, List<String> statuses, int limit,
                                                              int offset, String orderBy, String providerNo, boolean outOfDomain, boolean ignoreStatuses,
                                                              boolean ignoreMerged) {
        List<Demographic> list = new ArrayList<Demographic>();
        String queryString = "From Demographic d where d.LastName like :lastName ";

        String[] name = Objects.requireNonNullElse(searchStr, "").split(",");

        if (name.length == 2) {
            queryString += " and first_name like :firstName ";
        }

        if (statuses != null) {
            queryString += " and d.PatientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }
        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        // Session session = this.getSession();
        Session session = currentSession();
        try {
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("lastName", name[0].trim() + "%");
            if (name.length == 2) {
                q.setParameter("firstName", name[1].trim() + "%");
            }

            if (statuses != null) {
                q.setParameterList("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }

            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchMergedDemographicByName(String searchStr, int limit, int offset, String providerNo,
                                                           boolean outOfDomain) {
        List<Demographic> list = new ArrayList<Demographic>();
        String queryString = "From Demographic d where d.LastName like :lastName and d.HeadRecord is not null ";

        String[] name = searchStr.split(",");
        if (name.length == 2) {
            queryString += " and first_name like :firstName ";
        }

        // Session session = this.getSession();
        Session session = currentSession();
        try {
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("lastName", name[0].trim() + "%");
            if (name.length == 2) {
                q.setParameter("firstName", name[1].trim() + "%");
            }

            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
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
        String queryString = "FROM Demographic d WHERE d.Hin like :hin AND d.Sex = :gender AND d.YearOfBirth like :yearOfBirth AND d.MonthOfBirth like :monthOfBirth AND d.DateOfBirth like :dateOfBirth AND d.LastName = :lastName and d.PatientStatus != 'MERGED'";
        String[] params = dob.split("-");
        // Session session = this.getSession();
        Session session = currentSession();

        try {
            Query q = session.createQuery(queryString);
            q.setParameter("hin", hin.trim());
            q.setParameter("gender", gender.trim());
            q.setParameter("dateOfBirth", params[2].trim() + "%");
            q.setParameter("monthOfBirth", params[1].trim() + "%");
            q.setParameter("yearOfBirth", params[0].trim() + "%");
            q.setParameter("lastName", lastName.toUpperCase());
            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }

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
        String queryString = "From Demographic d where d.YearOfBirth like :yearOfBirth AND d.MonthOfBirth like :monthOfBirth AND d.DateOfBirth like :dateOfBirth ";

        // format must be yyyy-mm-dd
        String[] params = dobStr.split("-");
        if (params.length != 3) {
            return null;
        }

        if (params.length != 3)
            return new ArrayList<Demographic>();

        if (statuses != null) {
            queryString += " and d.PatientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }

        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        // Session session = this.getSession();
        Session session = currentSession();
        try {
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("yearOfBirth", params[0].trim() + "%");
            q.setParameter("monthOfBirth", params[1].trim() + "%");
            q.setParameter("dateOfBirth", params[2].trim() + "%");

            if (statuses != null) {
                q.setParameterList("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }

            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
        return list;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public List<Demographic> searchMergedDemographicByDOB(String dobStr, int limit, int offset, String providerNo,
                                                          boolean outOfDomain) {
        List<Demographic> list = new ArrayList<Demographic>();
        String queryString = "From Demographic d where d.YearOfBirth like :yearOfBirth AND d.MonthOfBirth like :monthOfBirth AND d.DateOfBirth like :dateOfBirth and d.HeadRecord is not null ";

        // format must be yyyy-mm-dd
        String[] params = dobStr.split("-");
        if (params.length != 3)
            return new ArrayList<Demographic>();

        // Session session = this.getSession();
        Session session = currentSession();
        try {
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("yearOfBirth", params[0].trim() + "%");
            q.setParameter("monthOfBirth", params[1].trim() + "%");
            q.setParameter("dateOfBirth", params[2].trim() + "%");

            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
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
        String queryString = "From Demographic d where (d.Phone like :phone OR d.Phone2 LIKE :phone)";

        if (statuses != null) {
            queryString += " and d.PatientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }
        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        // Session session = this.getSession();
        Session session = currentSession();
        try {
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("phone", "%" + phoneStr.trim() + "%");

            if (statuses != null) {
                q.setParameterList("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }

            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchMergedDemographicByPhone(String phoneStr, int limit, int offset, String providerNo,
                                                            boolean outOfDomain) {
        List<Demographic> list = new ArrayList<Demographic>();

        String queryString = "From Demographic d where d.Phone like :phone and d.HeadRecord is not null ";
        // Session session = this.getSession();
        Session session = currentSession();
        try {
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("phone", "%" + phoneStr.trim() + "%");

            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
        return list;
    }

    @Override
    public List<Demographic> searchDemographicByHIN(String hinStr) {
        List<Demographic> list = new ArrayList<Demographic>();

        String queryString = "From Demographic d where d.Hin like :hin and d.PatientStatus != 'MERGED' ";

        // Session session = this.getSession();
        Session session = currentSession();
        try {
            Query q = session.createQuery(queryString);

            q.setParameter("hin", hinStr.trim());

            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
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

        String queryString = "From Demographic d where d.Hin like :hin ";

        if (statuses != null) {
            queryString += " and d.PatientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }

        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        // Session session = this.getSession();
        Session session = currentSession();
        try {
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("hin", hinStr.trim() + "%");

            if (statuses != null) {
                q.setParameterList("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }

            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
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
     * @param orderByName   order by last name and first name
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
            sqlParameters.append(" and d.Hin like :hin");
        if (firstName != null)
            sqlParameters.append(" and d.FirstName like :firstName");
        if (lastName != null)
            sqlParameters.append(" and d.LastName like :lastName");
        if (gender != null)
            sqlParameters.append(" and d.Sex = :gender");

        if (dateOfBirth != null) {
            sqlParameters.append(" and d.YearOfBirth = :yearOfBirth");
            sqlParameters.append(" and d.MonthOfBirth = :monthOfBirth");
            sqlParameters.append(" and d.DateOfBirth = :dateOfBirth");
        }

        if (city != null)
            sqlParameters.append(" and d.City like :city");
        if (province != null)
            sqlParameters.append(" and d.Province = :province");
        if (phone != null)
            sqlParameters.append(" and (d.Phone like :phone or d.Phone2 like :phone)");
        if (email != null)
            sqlParameters.append(" and d.Email like :email");
        if (alias != null)
            sqlParameters.append(" and d.Alias like :alias");

        // at least 1 parameter must exist
        // we remove the first " and" because the first clause is after the "where" in
        // the sql statement.
        if (sqlParameters.length() == 0)
            throw (new IllegalArgumentException("at least one parameter must be present"));
        else {
            sqlCommand = "from Demographic d where"
                + sqlParameters.substring(" and".length(), sqlParameters.length())
                + (orderByName ? " order by d.LastName, d.FirstName" : "");
        }

        // Session session = this.getSession();
        Session session = currentSession();
        try {
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
            List<Demographic> results = query.list();

            return (results);
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchMergedDemographicByHIN(String hinStr, int limit, int offset, String providerNo,
                                                          boolean outOfDomain) {
        List<Demographic> list = new ArrayList<Demographic>();

        String queryString = "From Demographic d where d.Hin like :hin and d.HeadRecord is not null ";
        // Session session = this.getSession();
        Session session = currentSession();
        try {
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("hin", hinStr.trim() + "%");

            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
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

        String queryString = "From Demographic d where d.Address like :address ";

        if (statuses != null) {
            queryString += " and d.PatientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }

        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        // Session session = this.getSession();
        Session session = currentSession();
        try {
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("address", "%" + addressStr.trim() + "%");

            if (statuses != null) {
                q.setParameterList("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }
            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
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

        // Session session = this.getSession();
        Session session = currentSession();
        try {
            SQLQuery query = session.createSQLQuery(queryString);
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            query.setParameter("key", key.name());
            query.setParameter("value", "%" + value + "%");

            if (statuses != null) {
                query.setParameterList("statuses", statuses);
            }
            if (providerNo != null && !outOfDomain) {
                query.setParameter("providerNo", providerNo);
            }

            query.addEntity("de", Demographic.class); // tried to define
            list = query.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> searchMergedDemographicByAddress(String addressStr, int limit, int offset,
                                                              String providerNo, boolean outOfDomain) {
        List<Demographic> list = new ArrayList<Demographic>();

        String queryString = "From Demographic d where d.Address like :address and d.HeadRecord is not null ";

        // Session session = this.getSession();
        Session session = currentSession();
        try {
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("address", addressStr.trim() + "%");

            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
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

        String queryString = "From Demographic d where d.ChartNo like :chartNo ";

        if (statuses != null) {
            queryString += " and d.PatientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }

        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        // Session session = getSession();
        Session session = currentSession();
        List<Demographic> list = null;

        try {
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            q.setParameter("chartNo", chartNoStr.trim() + "%");

            if (statuses != null) {
                q.setParameterList("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }
            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
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
            queryString += " and d.DemographicNo = :demographicNo ";
        }
        if (statuses != null) {
            queryString += " and d.PatientStatus " + ((ignoreStatuses) ? "not" : "") + "  in (:statuses)";
        }

        if (providerNo != null && !outOfDomain) {
            queryString += " AND d.id IN (" + PROGRAM_DOMAIN_RESTRICTION + ") ";
        }

        if (orderBy != null) {
            queryString += " ORDER BY " + getOrderField(orderBy);
        }

        // Session session = getSession();
        Session session = currentSession();
        List<Demographic> list = null;

        try {
            Query q = session.createQuery(queryString);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            if (val != null) {
                q.setParameter("demographicNo", val);
            }
            if (statuses != null) {
                q.setParameterList("statuses", statuses);
            }

            if (providerNo != null && !outOfDomain) {
                q.setParameter("providerNo", providerNo);
            }
            list = q.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
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

        this.getHibernateTemplate().saveOrUpdate(demographic);

        if (OscarProperties.getInstance().isHL7A04GenerationEnabled() && !objExists) {
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
            orderBy = getOrderField(orderBy);
        } else {
            if (orderBy.equals("dob")) {
                orderBy = "de.year_of_birth, de.month_of_birth, de.date_of_birth ";
            } else {
                orderBy = "de." + orderBy;
            }
        }

        return orderBy;
    }

    @Override
    public String getOrderField(String orderBy) {

        String orderByField = "d.LastName,d.FirstName";

        if (orderBy.equals("last_name") || orderBy.equals("last_name, first_name")) {
            orderByField = "d.LastName, d.FirstName";
        } else if (orderBy.equals("demographic_no")) {
            orderByField = "d.DemographicNo";
        } else if (orderBy.equals("chart_no")) {
            orderByField = "d.ChartNo";
        } else if (orderBy.equals("sex")) {
            orderByField = "d.Sex";
        } else if (orderBy.equals("dob")) {
            orderByField = "d.DateOfBirth";
        } else if (orderBy.equals("provider_no")) {
            orderByField = "d.ProviderNo";
        } else if (orderBy.equals("roster_status")) {
            orderByField = "d.RosterStatus";
        } else if (orderBy.equals("patient_status")) {
            orderByField = "d.PatientStatus";
        } else if (orderBy.equals("phone")) {
            orderByField = "d.Phone";
        }
        return orderByField;
    }

    @Override
    public List<Integer> getDemographicIdsAlteredSinceTime(Date value) {
        Connection c = null;
        try {
            c = DbConnectionFilter.getThreadLocalDbConnection();
            PreparedStatement ps = c.prepareStatement(
                "SELECT DISTINCT demographic_no FROM log WHERE dateTime >= ? and action != 'read'");
            ps.setTimestamp(1, new Timestamp(value.getTime()));
            ResultSet rs = ps.executeQuery();
            ArrayList<Integer> results = new ArrayList<Integer>();
            while (rs.next()) {
                if (rs.getInt(1) != 0) {
                    results.add(rs.getInt(1));
                }
            }
            return (results);
        } catch (SQLException e) {
            throw (new PersistenceException(e));
        } finally {
            SqlUtils.closeResources(c, null, null);
        }
    }

    @Override
    public List<Integer> getDemographicIdsOpenedChartSinceTime(String value) {
        Connection c = null;
        try {
            c = DbConnectionFilter.getThreadLocalDbConnection();
            PreparedStatement ps = c.prepareStatement(
                "SELECT DISTINCT contentId FROM log WHERE dateTime >= ? AND content='eChart' GROUP BY contentId");
            ps.setString(1, value);
            ResultSet rs = ps.executeQuery();
            ArrayList<Integer> results = new ArrayList<Integer>();
            while (rs.next()) {
                results.add(rs.getInt(1));
            }
            return (results);
        } catch (SQLException e) {
            throw (new PersistenceException(e));
        } finally {
            SqlUtils.closeResources(c, null, null);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getRosterStatuses() {
        List<String> results = (List<String>) getHibernateTemplate().find(
            "SELECT DISTINCT d.RosterStatus FROM Demographic d where d.RosterStatus != '' and d.RosterStatus != 'RO' and d.RosterStatus != 'TE' and d.RosterStatus != 'FS'");
        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getAllRosterStatuses() {
        List<String> results = (List<String>) getHibernateTemplate().find(
            "SELECT DISTINCT d.RosterStatus FROM Demographic d where d.RosterStatus is not null order by d.RosterStatus");
        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getAllPatientStatuses() {
        List<String> results = (List<String>) getHibernateTemplate().find(
            "SELECT DISTINCT d.PatientStatus FROM Demographic d where d.PatientStatus is not null order by d.PatientStatus");
        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> search_ptstatus() {
        List<String> results = (List<String>) getHibernateTemplate().find(
            "SELECT DISTINCT d.PatientStatus FROM Demographic d where d.PatientStatus is not null and d.PatientStatus <> '' and d.PatientStatus <> 'AC' and d.PatientStatus <> 'IN' and d.PatientStatus <> 'DE' and d.PatientStatus <> 'MO' and d.PatientStatus <> 'FI' order by d.PatientStatus");
        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getAllProviderNumbers() {
        List<String> results = (List<String>) getHibernateTemplate()
            .find("SELECT DISTINCT d.ProviderNo FROM Demographic d order by d.ProviderNo");
        return results;
    }

    /// ////////////// CLIENT DAO MERGED ///////////////////////////

    /*
     * (non-Javadoc)
     *
     * @see ca.openosp.openo.PMmodule.daos.DemographicDao#clientExists(java.lang.Integer)
     */
    @Override
    public boolean clientExists(Integer demographicNo) {

        boolean exists = getHibernateTemplate().get(Demographic.class, demographicNo) != null;
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
            this.getHibernateTemplate().evict(existingDemo);

        log.debug("exists (then evict): " + exists);

        return exists;
    }

    @Override
    public Demographic getClientByDemographicNo(Integer demographicNo) {

        if (demographicNo == null || demographicNo.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        Demographic result = getHibernateTemplate().get(Demographic.class, demographicNo);

        if (log.isDebugEnabled()) {
            log.debug("getClientByDemographicNo: id=" + demographicNo + ", found=" + (result != null));
        }

        return result;
    }

    @Override
    public List<Demographic> getClients() {
        logger.error(
            "No one should be calling this method, this is a good way to run out of memory and crash a server... this is too large of a result set, it should be pagenated.",
            new IllegalArgumentException("The entire demographic table is too big to allow a full select."));

        String queryStr = " FROM Demographic";
        @SuppressWarnings("unchecked")
        List<Demographic> rs = (List<Demographic>) getHibernateTemplate().find(queryStr);

        if (log.isDebugEnabled()) {
            log.debug("getClients: # of results=" + rs.size());
        }

        return rs;
    }

    // Quatro Merge
    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> search(ClientSearchFormBean bean, boolean returnOptinsOnly, boolean excludeMerged) {
        // Session session = this.getSession();
        Session session = currentSession();

        Criteria criteria = session.createCriteria(Demographic.class);
        String firstName = "";
        String lastName = "";
        String firstNameL = "";
        String lastNameL = "";
        String assignedToProviderNo = "";

        String active = "";
        String gender = "";

        String sql = "";

        List<Demographic> results = null;

        if (bean.getFirstName() != null && bean.getFirstName().length() > 0) {
            firstName = bean.getFirstName();
            firstNameL = firstName + "%";
        }

        if (bean.getLastName() != null && bean.getLastName().length() > 0) {
            lastName = bean.getLastName();
            lastNameL = lastName + "%";
        }

        String clientNo = bean.getDemographicNo();
        // exclude merged client
        if (excludeMerged)
            criteria.add(Expression.eq("merged", Boolean.FALSE));
        if (clientNo != null && !"".equals(clientNo)) {
            if (Utility.IsInt(clientNo)) {
                criteria.add(Expression.eq("DemographicNo", Integer.valueOf(clientNo)));
                results = criteria.list();
            } else {
                /* invalid client no generates a empty search results */
                results = new ArrayList<Demographic>();
            }
            // this.releaseSession(session);
            //session.close();
            return results;
        }

        if (firstName.length() > 0) {
            // sql = "(LEFT(SOUNDEX(first_name),4) = LEFT(SOUNDEX('" + firstName + "'),4))";
            // sql2 = "(LEFT(SOUNDEX(alias),4) = LEFT(SOUNDEX('" + firstName + "'),4))";
            // condFirstName = Restrictions.or(Restrictions.ilike("FirstName", firstNameL),
            // Restrictions.sqlRestriction(sql));
            // condAlias1 = Restrictions.or(Restrictions.ilike("Alias",
            // firstNameL),Restrictions.sqlRestriction(sql2));
            criteria.add(Restrictions.or(Restrictions.or(Restrictions.ilike("LastName", firstNameL),
                Restrictions.ilike("Alias", firstNameL)), Restrictions.ilike("FirstName", firstNameL)));
        }
        if (lastName.length() > 0) {
            // sql = "(LEFT(SOUNDEX(last_name),4) = LEFT(SOUNDEX('" + lastName + "'),4))";
            // sql2 = "(LEFT(SOUNDEX(alias),4) = LEFT(SOUNDEX('" + lastName + "'),4))";
            // condLastName = Restrictions.or(Restrictions.ilike("LastName", lastNameL),
            // Restrictions.sqlRestriction(sql));
            // condAlias2 = Restrictions.or(Restrictions.ilike("Alias",
            // lastNameL),Restrictions.sqlRestriction(sql2));
            criteria.add(Restrictions.or(
                Restrictions.or(Restrictions.ilike("FirstName", lastNameL), Restrictions.ilike("Alias", lastNameL)),
                Restrictions.ilike("LastName", lastNameL)));
        }
        /*
         * if (firstName.length() > 0 && lastName.length()>0)
         * {
         * criteria.add(Restrictions.or(Restrictions.and(condFirstName, condLastName),
         * Restrictions.or(condAlias1, condAlias2)));
         * }
         * else if (firstName.length() > 0)
         * {
         * criteria.add(Restrictions.or(condFirstName,condAlias1));
         * }
         * else if (lastName.length()>0)
         * {
         * criteria.add(Restrictions.or(condLastName,condAlias2));
         * }
         */
        if (bean.getDob() != null && bean.getDob().length() > 0) {
            criteria.add(Expression.eq("DateOfBirth", MyDateFormat.getCalendar(bean.getDob())));
        }

        if (bean.getHealthCardNumber() != null && bean.getHealthCardNumber().length() > 0) {
            criteria.add(Expression.eq("Hin", bean.getHealthCardNumber()));
        }

        if (bean.getHealthCardVersion() != null && bean.getHealthCardVersion().length() > 0) {
            criteria.add(Expression.eq("Ver", bean.getHealthCardVersion()));
        }

        if (bean.getAssignedToProviderNo() != null && bean.getAssignedToProviderNo().length() > 0) {
            assignedToProviderNo = bean.getAssignedToProviderNo();
            sql = " demographic_no in (select decode(dm.merged_to,null,a.client_id,dm.merged_to) from admission a,demographic_merged dm where a.client_id=dm.demographic_no(+)and a.primaryWorker='"
                + assignedToProviderNo + "')";
            criteria.add(Restrictions.sqlRestriction(sql));
        }

        active = bean.getActive();
        if ("1".equals(active)) {
            criteria.add(Expression.ge("activeCount", Integer.valueOf(1)));
        } else if ("0".equals(active)) {
            criteria.add(Expression.eq("activeCount", Integer.valueOf(0)));
        }

        gender = bean.getGender();
        if (gender != null && !"".equals(gender)) {
            criteria.add(Expression.eq("Sex", gender));
        }
        criteria.addOrder(Order.asc("LastName"));
        criteria.addOrder(Order.asc("FirstName"));
        results = criteria.list();

        if (log.isDebugEnabled()) {
            log.debug("search: # of results=" + results.size());
        }
        // this.releaseSession(session);
        //session.close();
        return results;
    }

    /*
     * use program_client table to do domain based search
     */
    @Override
    public List<Demographic> search(ClientSearchFormBean bean) {

        // Session session = this.getSession();
        Session session = currentSession();
        Criteria criteria = session.createCriteria(Demographic.class);
        String firstName = "";
        String lastName = "";
        String firstNameL = "";
        String lastNameL = "";

        String active = "";
        String gender = "";

        String sql = "";
        String sql2 = "";

        @SuppressWarnings("unchecked")
        List<Demographic> results = null;

        if (bean.getFirstName() != null && bean.getFirstName().length() > 0) {
            firstName = bean.getFirstName();
            firstNameL = "%" + firstName + "%";
        }

        if (bean.getLastName() != null && bean.getLastName().length() > 0) {
            lastName = bean.getLastName();
            lastNameL = "%" + lastName + "%";
        }

        String clientNo = bean.getDemographicNo();
        if (clientNo != null && !"".equals(clientNo)) {
            if (Utility.IsInt(clientNo)) {
                criteria.add(Expression.eq("DemographicNo", Integer.valueOf(clientNo).intValue()));
                results = criteria.list();
            } else {
                /* invalid client no generates a empty search results */
                results = new ArrayList<Demographic>();
            }
            // releaseSession(session);
            //session.close();
            return results;
        }
        LogicalExpression condAlias1 = null;
        LogicalExpression condAlias2 = null;
        LogicalExpression condFirstName = null;
        LogicalExpression condLastName = null;

        if (firstName.length() > 0) {
            sql = "(LEFT(SOUNDEX(first_name),2) = LEFT(SOUNDEX('" + firstName + "'),2))";
            sql2 = "(LEFT(SOUNDEX(alias),2) = LEFT(SOUNDEX('" + firstName + "'),2))";
            condFirstName = Restrictions.or(Restrictions.ilike("FirstName", firstNameL),
                Restrictions.sqlRestriction(sql));
            condAlias1 = Restrictions.or(Restrictions.ilike("Alias", firstNameL), Restrictions.sqlRestriction(sql2));

        }
        if (lastName.length() > 0) {
            sql = "(LEFT(SOUNDEX(last_name),2) = LEFT(SOUNDEX('" + lastName + "'),2))";
            sql2 = "(LEFT(SOUNDEX(alias),2) = LEFT(SOUNDEX('" + lastName + "'),2))";
            condLastName = Restrictions.or(Restrictions.ilike("LastName", lastNameL), Restrictions.sqlRestriction(sql));
            condAlias2 = Restrictions.or(Restrictions.ilike("Alias", lastNameL), Restrictions.sqlRestriction(sql2));
        }

        if (bean.getChartNo() != null && bean.getChartNo().length() > 0) {
            criteria.add(Expression.like("ChartNo", "%" + bean.getChartNo() + "%"));
        }

        if (!bean.isSearchUsingSoundex()) {

            if (firstName.length() > 0) {
                criteria.add(Restrictions.or(Restrictions.ilike("FirstName", firstNameL),
                    Restrictions.ilike("Alias", firstNameL)));
            }
            if (lastName.length() > 0) {
                criteria.add(Restrictions.or(Restrictions.ilike("LastName", lastNameL),
                    Restrictions.ilike("Alias", lastNameL)));
            }
        } else { // soundex variation

            if (firstName.length() > 0) {
                criteria.add(Restrictions.or(condFirstName, condAlias1));
            }
            if (lastName.length() > 0) {
                criteria.add(Restrictions.or(condLastName, condAlias2));
            }
        }

        if (bean.getDob() != null && bean.getDob().length() > 0) {
            criteria.add(Expression.eq("YearOfBirth", bean.getYearOfBirth()));
            criteria.add(Expression.eq("MonthOfBirth", bean.getMonthOfBirth()));
            criteria.add(Expression.eq("DateOfBirth", bean.getDayOfBirth()));
        }

        if (bean.getHealthCardNumber() != null && bean.getHealthCardNumber().length() > 0) {
            criteria.add(Expression.eq("Hin", bean.getHealthCardNumber()));
        }

        if (bean.getHealthCardVersion() != null && bean.getHealthCardVersion().length() > 0) {
            criteria.add(Expression.eq("Ver", bean.getHealthCardVersion()));
        }

        if (bean.getChartNo() != null && bean.getChartNo().length() > 0) {
            criteria.add(Expression.like("ChartNo", "%" + bean.getChartNo() + "%"));
        }

        if (!bean.isSearchOutsideDomain()) {
            // program domain limited search
            if (bean.getProgramDomain() == null) {
                bean.setProgramDomain(new ArrayList<ProgramProvider>());
            }

            DetachedCriteria subq = DetachedCriteria.forClass(Admission.class)
                .setProjection(Property.forName("clientId"));

            StringBuilder programIds = new StringBuilder();
            for (int x = 0; x < bean.getProgramDomain().size(); x++) {
                ProgramProvider p = (ProgramProvider) bean.getProgramDomain().get(x);
                if (x > 0) {
                    programIds.append(",");
                }
                programIds.append(p.getProgramId());
            }

            String[] pIds = {};
            pIds = programIds.toString().split(",");
            logger.info("programIds is " + programIds.toString());

            if (programIds.length() == 0) {
                logger.info("providers not staff in any program, ie. can't see ANYONE.");
                // providers not staff in any program, ie. can't see ANYONE.
                return new ArrayList<Demographic>();
            }
            Integer[] pIdi = new Integer[pIds.length];
            for (int i = 0; i < pIds.length; i++) {
                pIdi[i] = Integer.parseInt(pIds[i]);
            }

            if (pIdi.length > 0) {
                subq.add(Restrictions.in("programId", (Object[]) pIdi));
            }

            if (bean.getDateFrom() != null && bean.getDateFrom().length() > 0) {
                Date dt = MyDateFormat.getSysDate(bean.getDateFrom().trim());
                subq.add(Restrictions.ge("admissionDate", dt));
            }
            if (bean.getDateTo() != null && bean.getDateTo().length() > 0) {
                Date dt1 = MyDateFormat.getSysDate(bean.getDateTo().trim());
                subq.add(Restrictions.le("admissionDate", dt1));
            }

            criteria.add(Property.forName("DemographicNo").in(subq));
        }

        active = bean.getActive();
        if ("1".equals(active)) {
            criteria.add(Expression.ge("activeCount", 1));
        } else if ("0".equals(active)) {
            criteria.add(Expression.eq("activeCount", 0));
        }

        gender = bean.getGender();
        if (gender != null && !"".equals(gender)) {
            criteria.add(Expression.eq("Sex", gender));
        }

        criteria.add(Expression.or(Expression.ne("anonymous", "one-time-anonymous"), Expression.isNull("anonymous")));

        results = criteria.list();

        if (log.isDebugEnabled()) {
            log.debug("search: # of results=" + results.size());
        }

        // this.releaseSession(session);
        //session.close();
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
        this.getHibernateTemplate().saveOrUpdate(client);

        if (OscarProperties.getInstance().isHL7A04GenerationEnabled() && !objExists)
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

        StringBuilder sqlCommand = new StringBuilder();
        boolean joinCaseMgmtNote = StringUtils.trimToNull(x.getProviderId()) != null
            || StringUtils.trimToNull(x.getSeenStartDate()) != null
            || StringUtils.trimToNull(x.getSeenEndDate()) != null;

        // this is a horrid join, no one is allowed to give me grief about it, until we
        // refactor *everything*, some nasty hacks will happen.
        sqlCommand.append("select * from demographic" + (joinCaseMgmtNote ? ",casemgmt_note" : "")
            + ",admission,program where demographic.demographic_no=admission.client_id"
            + (joinCaseMgmtNote ? " and demographic.demographic_no=casemgmt_note.demographic_no" : "")
            + " and admission.program_id=program.id");

        // status
        if (StringUtils.trimToNull(x.getAdmissionStatus()) != null)
            sqlCommand.append(" and demographic.patient_status=?");

        // providers
        if (StringUtils.trimToNull(x.getProviderId()) != null)
            sqlCommand.append(" and casemgmt_note.provider_no=?");

        // seen date
        if (StringUtils.trimToNull(x.getSeenStartDate()) != null)
            sqlCommand.append(" and casemgmt_note.update_date>=?");
        if (StringUtils.trimToNull(x.getSeenEndDate()) != null)
            sqlCommand.append(" and casemgmt_note.update_date<=?");

        // program
        if (StringUtils.trimToNull(x.getProgramId()) != null)
            sqlCommand.append(" and admission.program_id=?");

        // admission date
        if (StringUtils.trimToNull(x.getEnrolledStartDate()) != null)
            sqlCommand.append(" and admission.admission_date>=?");
        if (StringUtils.trimToNull(x.getEnrolledEndDate()) != null)
            sqlCommand.append(" and admission.admission_date<=?");

        sqlCommand.append(" order by last_name,first_name");

        // yeah I know using a treeMap isn't an efficient way of making this unique but
        // given the current constraints this was quick and dirty and should work for
        // the size of our data set
        TreeMap<String, ClientListsReportResults> results = new TreeMap<String, ClientListsReportResults>();

        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            c = DbConnectionFilter.getThreadLocalDbConnection();
            ps = c.prepareStatement(sqlCommand.toString());

            // filter by providers
            String temp;
            int parameterPosition = 1;

            // status
            if ((temp = StringUtils.trimToNull(x.getAdmissionStatus())) != null)
                ps.setString(parameterPosition++, temp);

            // providers
            if ((temp = StringUtils.trimToNull(x.getProviderId())) != null)
                ps.setString(parameterPosition++, temp);

            // seen date
            // yes I know the date format is crap and is error prone and will return bad
            // messages to the user, I don't care right now, we'll fix it after a re-write
            if ((temp = StringUtils.trimToNull(x.getSeenStartDate())) != null)
                ps.setString(parameterPosition++, temp);
            if ((temp = StringUtils.trimToNull(x.getSeenEndDate())) != null)
                ps.setString(parameterPosition++, temp);

            // program
            if ((temp = StringUtils.trimToNull(x.getProgramId())) != null)
                ps.setString(parameterPosition++, temp);

            // admission date
            // yes I know the date format is crap and is error prone and will return bad
            // messages to the user, I don't care right now, we'll fix it after a re-write
            if ((temp = StringUtils.trimToNull(x.getEnrolledStartDate())) != null)
                ps.setString(parameterPosition++, temp);
            if ((temp = StringUtils.trimToNull(x.getEnrolledEndDate())) != null)
                ps.setString(parameterPosition++, temp);

            rs = ps.executeQuery();
            while (rs.next()) {
                ClientListsReportResults clientListsReportResults = new ClientListsReportResults();
                clientListsReportResults.admissionId = rs.getInt("admission.am_id");

                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(0);
                calendar.set(Calendar.YEAR, Integer.parseInt(Misc.getString(rs, "demographic.year_of_birth")));
                calendar.set(Calendar.MONTH, rs.getInt("demographic.month_of_birth") - 1);
                calendar.set(Calendar.DAY_OF_MONTH, rs.getInt("demographic.date_of_birth"));
                clientListsReportResults.dateOfBirth = calendar;

                clientListsReportResults.demographicId = rs.getInt("demographic.demographic_no");
                clientListsReportResults.firstName = Misc.getString(rs, "demographic.first_name");
                clientListsReportResults.lastName = Misc.getString(rs, "demographic.last_name");
                clientListsReportResults.programId = rs.getInt("program.id");
                clientListsReportResults.programName = Misc.getString(rs, "program.name");

                results.put(clientListsReportResults.lastName + clientListsReportResults.firstName,
                    clientListsReportResults);
            }
        } catch (SQLException e) {
            throw (new HibernateException(e));
        } finally {
            // odd not sure what the stupid spring template is doing here but I have to
            // close the session.
            SqlUtils.closeResources(c, ps, rs);
        }

        return results;
    }

    @Override
    public List<Demographic> getClientsByChartNo(String chartNo) {
        String queryStr = " FROM Demographic d where d.ChartNo=?0";
        @SuppressWarnings("unchecked")
        List<Demographic> rs = (List<Demographic>) getHibernateTemplate().find(queryStr, new Object[]{chartNo});

        if (log.isDebugEnabled()) {
            log.debug("getClientsByChartNo: # of results=" + rs.size());
        }

        return rs;
    }

    @Override
    public List<Demographic> getClientsByHealthCard(String num, String type) {
        String queryStr = " FROM Demographic d where d.Hin=?0 and d.HcType=?1";
        @SuppressWarnings("unchecked")
        List<Demographic> rs = (List<Demographic>) getHibernateTemplate().find(queryStr, new Object[]{num, type});

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
        int paramIndex = 0;

        if (firstName.trim().length() > 0) {
            whereClause.append("FirstName=?" + paramIndex++);
            params.add(firstName.trim());
        }
        if (lastName.trim().length() > 0) {
            if (params.size() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("LastName=?" + paramIndex++);
            params.add(lastName.trim());
        }
        if (hPhone.trim().length() > 0) {
            if (params.size() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("Phone=?" + paramIndex++);
            params.add(hPhone.trim());
        }
        if (wPhone.trim().length() > 0) {
            if (params.size() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("Phone2=?" + paramIndex++);
            params.add(wPhone.trim());
        }
        if (email.trim().length() > 0) {
            if (params.size() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("Email=?" + paramIndex++);
            params.add(email.trim());
        }

        if (whereClause.length() == 0) {
            throw new IllegalArgumentException("you need to search by something");
        }
        String sql = "FROM Demographic WHERE " + whereClause;

        @SuppressWarnings("unchecked")
        List<Demographic> demographics = (List<Demographic>) this.getHibernateTemplate().find(sql,
            (Object[]) params.toArray(new String[params.size()]));

        if (!demographics.isEmpty()) {
            return demographics.get(0);
        }

        return null;
    }

    @Override
    public List<Demographic> searchByHealthCard(String hin) {
        String queryStr = " FROM Demographic d where d.Hin=?0";
        @SuppressWarnings("unchecked")
        List<Demographic> rs = (List<Demographic>) getHibernateTemplate().find(queryStr, new Object[]{hin});

        return rs;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> getDemographicWithLastFirstDOB(String lastname, String firstname, String year_of_birth,
                                                            String month_of_birth, String date_of_birth) {
        List<String> params = new ArrayList<String>();
        String sql = "FROM Demographic " + " WHERE LastName like ?0 and FirstName like ?1";
        params.add(lastname + "%");
        params.add(firstname + "%");

        int paramIndex = 2;
        if (year_of_birth != null) {
            sql += " AND YearOfBirth = ?" + paramIndex++;
            params.add(year_of_birth);
        }
        if (month_of_birth != null) {
            sql += " AND MonthOfBirth = ?" + paramIndex++;
            params.add(month_of_birth);
        }
        if (date_of_birth != null) {
            sql += " AND DateOfBirth = ?" + paramIndex++;
            params.add(date_of_birth);
        }

        return (List<Demographic>) this.getHibernateTemplate().find(sql, (Object[]) params.toArray(new String[params.size()]));
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> getDemographicWithLastFirstDOBExact(String lastname, String firstname,
                                                                 String year_of_birth, String month_of_birth, String date_of_birth) {
        List<String> params = new ArrayList<String>();
        String sql = "FROM Demographic " + " WHERE LastName = ?0 and FirstName = ?1";
        params.add(lastname);
        params.add(firstname);

        int paramIndex = 2;
        if (year_of_birth != null) {
            sql += " AND YearOfBirth = ?" + paramIndex++;
            params.add(year_of_birth);
        }
        if (month_of_birth != null) {
            sql += " AND MonthOfBirth = ?" + paramIndex++;
            params.add(month_of_birth);
        }
        if (date_of_birth != null) {
            sql += " AND DateOfBirth = ?" + paramIndex++;
            params.add(date_of_birth);
        }

        return (List<Demographic>) this.getHibernateTemplate().find(sql, (Object[]) params.toArray(new String[params.size()]));
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> getDemographicsByHealthNum(String hin) {
        String sSQL = "from Demographic d where d.Hin=?0";
        return (List<Demographic>) this.getHibernateTemplate().find(sSQL, new Object[]{hin});
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Integer> getActiveDemographicIds() {
        String sSQL = "select d.DemographicNo from Demographic d where d.PatientStatus=?0";
        return (List<Integer>) this.getHibernateTemplate().find(sSQL, new Object[]{"AC"});
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Integer> getDemographicIds() {
        String sSQL = "select d.DemographicNo from Demographic d";
        return (List<Integer>) this.getHibernateTemplate().find(sSQL);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> getDemographicWithGreaterThanYearOfBirth(int yearOfBirth) {
        String sSQL = "from Demographic d where d.YearOfBirth > ?0";
        return (List<Demographic>) this.getHibernateTemplate().find(sSQL, new Object[]{String.valueOf(yearOfBirth)});
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> search_catchment(String rosterStatus, int offset, int limit) {
        String sql = "from Demographic d where d.RosterStatus=:status and (d.Postal not like 'L0R%' and d.Postal not like 'L3M%' and d.Postal not like 'L8E%' and d.Postal not like 'L9A%' and d.Postal not like 'L8G%' and d.Postal not like 'L9B%' and d.Postal not like 'L8H%' and d.Postal not like 'L9C%' and d.Postal not like 'L8J%' and d.Postal not like 'L9G%' and d.Postal not like 'L8K%' and d.Postal not like 'L9H%' and d.Postal not like 'L8L%' and d.Postal not like 'L9K%' and d.Postal not like 'L8M%' and d.Postal not like 'L8N%' and d.Postal not like 'N0A%' and d.Postal not like 'L8P%' and d.Postal not like 'N3W%' and d.Postal not like 'L8R%' and d.Postal not like 'L8S%' and d.Postal not like 'L8T%' and d.Postal not like 'L8V%' and d.Postal not like 'L8W%' and d.Postal not like 'K8R%' and d.Postal not like 'L0R%' and d.Postal not like 'L5P%' and d.Postal not like 'L8A%' and d.Postal not like 'L8B%' and d.Postal not like 'L8C%' and d.Postal not like 'L8L%' and d.Postal not like 'L9L%' and d.Postal not like 'L9N%' and d.Postal not like 'L9S%' and d.Postal not like 'M9C%' and d.Postal not like 'N0B%1L0' and d.Postal not like 'L7L%' and d.Postal not like 'L7M%' and d.Postal not like 'L7N%' and d.Postal not like 'L7P%' and d.Postal not like 'L7R%' and d.Postal not like 'L7S%' and d.Postal not like 'L7T%' )";
        // Session s = getSession();
        Session s = currentSession();

        try {
            Query q = s.createQuery(sql);
            q.setParameter("status", rosterStatus);
            q.setMaxResults(limit);
            q.setFirstResult(offset);
            return q.list();
        } finally {
            // this.releaseSession(s);
            //s.close();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Demographic> findByField(String fieldName, Object fieldValue, String orderBy, int offset) {
        boolean isFieldValueEmpty = fieldValue == null || fieldValue.equals("");

        String sql = "FROM Demographic d WHERE d." + fieldName + " LIKE :fieldValue";
        if (isFieldValueEmpty) {
            sql = "FROM Demographic d";
        }

        if (orderBy != null && !orderBy.isEmpty()) {
            sql = sql + " ORDER BY d." + orderBy;
        }

        // Session s = getSession();
        Session s = currentSession();
        try {
            Query q = s.createQuery(sql);
            if (!isFieldValueEmpty) {
                q.setParameter("fieldValue", fieldValue);
            }

            q.setMaxResults(10);

            if (offset > 0) {
                q.setFirstResult(offset);
            }
            return q.list();
        } finally {
            // this.releaseSession(s);
            //s.close();

        }
    }

    @SuppressWarnings("unchecked")
    public List<Demographic> findByCriterion(DemographicCriterion c) {
        if (c.getHealthNumber() == null || c.getHealthNumber().trim().isEmpty()) {
            String sSQL = "FROM Demographic d " + "WHERE d.LastName like ?0 " + "AND d.FirstName like ?1 "
                + "AND d.YearOfBirth = ?2 " + "AND d.MonthOfBirth = ?3 " + "AND d.DateOfBirth = ?4 "
                + "AND d.Sex like ?5 " + "AND d.PatientStatus = ?6";
            return (List<Demographic>) this.getHibernateTemplate()
                .find(sSQL, c.getAll(false));
        }

        String sSQL = "FROM Demographic d " + "WHERE d.Hin = ?0 " + "AND d.LastName like ?1 " + "AND d.FirstName like ?2 "
            + "AND d.YearOfBirth = ?3 " + "AND d.MonthOfBirth = ?4 " + "AND d.DateOfBirth = ?5 "
            + "AND d.Sex like ?6 " + "AND d.PatientStatus = ?7";
        return (List<Demographic>) this.getHibernateTemplate().find(sSQL, c.getAll(true));
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
            sql = sql + " and provider_no = '" + providerNo + "' ";
        }
        sql = sql + " order by last_name ";

        // Session session = getSession();
        Session session = currentSession();
        try {
            SQLQuery sqlQuery = session.createSQLQuery(sql);
            return sqlQuery.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Integer> getActiveDemographicIdsOlderThan(int age) {
        List<Integer> ids = new ArrayList<Integer>();
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, Integer.parseInt(String.valueOf("-" + (age + 1))));

        List<Object[]> demographics = (List<Object[]>) getHibernateTemplate().find(
            "SELECT d.DemographicNo,d.YearOfBirth,d.MonthOfBirth,d.DateOfBirth FROM Demographic d WHERE d.PatientStatus = 'AC'");
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
        String sSQL = "select d.DemographicNo from Demographic d where d.lastUpdateDate >?0";
        return (List<Integer>) this.getHibernateTemplate().find(sSQL, value);
    }

    protected final void setLimit(Query query, int itemsToReturn) {
        if (itemsToReturn > MAX_SELECT_SIZE)
            throw (new IllegalArgumentException("Requested too large of a result list size : " + itemsToReturn));

        query.setMaxResults(itemsToReturn);
    }

    protected final void setLimit(SQLQuery query, int itemsToReturn) {
        if (itemsToReturn > MAX_SELECT_SIZE)
            throw (new IllegalArgumentException("Requested too large of a result list size : " + itemsToReturn));

        query.setMaxResults(itemsToReturn);
    }

    @Override
    public List<Demographic> getDemographicByRosterStatus(String rosterStatus, String patientStatus) {
        if (patientStatus == null || patientStatus == "") {
            patientStatus = "AC";
        }
        String queryStr = " FROM Demographic d where d.RosterStatus=?0 and d.PatientStatus = ?1";
        Object[] params = new Object[]{rosterStatus, patientStatus};
        @SuppressWarnings("unchecked")
        List<Demographic> rs = (List<Demographic>) getHibernateTemplate().find(queryStr, params);

        return rs;
    }

    @Override
    public Integer searchPatientCount(LoggedInInfo loggedInInfo, DemographicSearchRequest searchRequest) {
        Map<String, Object> params = new HashMap<String, Object>();

        String demographicQuery = generateDemographicSearchQuery(loggedInInfo, searchRequest, params, "count(*)");

        MiscUtils.getLogger().warn(demographicQuery);

        // Session session = getSession();
        Session session = currentSession();
        try {
            SQLQuery sqlQuery = session.createSQLQuery(demographicQuery);
            for (String key : params.keySet()) {
                sqlQuery.setParameter(key, params.get(key));
                MiscUtils.getLogger().warn(key + "=" + params.get(key));
            }
            Integer result = ((BigInteger) sqlQuery.uniqueResult()).intValue();
            return result;
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
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

        // Session session = getSession();
        Session session = currentSession();
        try {
            SQLQuery sqlQuery = session.createSQLQuery(demographicQuery);

            for (String key : params.keySet()) {
                sqlQuery.setParameter(key, params.get(key));
            }

            sqlQuery.setFirstResult(startIndex);
            DemographicSearchResultTransformer transformer = new DemographicSearchResultTransformer();
            transformer.setDemographicDao(this);
            sqlQuery.setResultTransformer(transformer);
            setLimit(sqlQuery, itemsToReturn);

            return sqlQuery.list();
        } finally {
            // this.releaseSession(session);
            //session.close();
        }
    }

    private String generateDemographicSearchQuery(LoggedInInfo loggedInInfo, DemographicSearchRequest searchRequest,
                                                  Map<String, Object> params, String select) {
        OscarProperties props = OscarProperties.getInstance();
        MatchingDemographicParameters matchingDemographicParameters = null;

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
            matchingDemographicParameters = new MatchingDemographicParameters();
            matchingDemographicParameters.setHin(searchRequest.getKeyword());
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

                GregorianCalendar cal = new GregorianCalendar(Integer.parseInt(year), Integer.parseInt(month) - 1,
                    Integer.parseInt(day));
                matchingDemographicParameters = new MatchingDemographicParameters();
                matchingDemographicParameters.setBirthDate(cal);
            } catch (Exception e) {
                // this is okay, person inputed a bad date, we'll ignore for now
                matchingDemographicParameters = null;
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
            matchingDemographicParameters = new MatchingDemographicParameters();
            String[] lastfirst = searchRequest.getKeyword().split(",");

            if (lastfirst.length > 1) {
                matchingDemographicParameters.setLastName(lastfirst[0].trim());
                matchingDemographicParameters.setFirstName(lastfirst[1].trim());
            } else {
                matchingDemographicParameters.setLastName(lastfirst[0].trim());
            }

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

        String q = "FROM Demographic d WHERE d.DemographicNo in (:ids)";
        @SuppressWarnings("unchecked")
        List<Demographic> results = (List<Demographic>) getHibernateTemplate().findByNamedParam(q, "ids",
            demographicIds);
        return (results);

    }


    @SuppressWarnings("unchecked")
    @Override
    public List<Integer> getMissingExtKey(String keyName) {
        // Session session = getSession();
        Session session = currentSession();
        try {
            SQLQuery sqlQuery = session.createSQLQuery(
                "select distinct d.demographic_no from demographic d where d.demographic_no not in (select distinct d.demographic_no from demographic d, demographicExt e where d.demographic_no = e.demographic_no and key_val=:key)");
            sqlQuery.setString("key", keyName);
            List<Integer> ids = sqlQuery.list();

            return ids;
        } finally {
            // this.releaseSession(session);
            //session.close();
        }

    }


    @Override
    public List<Demographic> getActiveDemographicAfter(Date afterDatetimeExclusive) {
        String q = "From Demographic d where d.PatientStatus='AC'";
        if (afterDatetimeExclusive != null) {
            q += " and d.lastUpdateDate > ?0";
        }

        List<Demographic> rs = null;
        rs = afterDatetimeExclusive != null ? (List<Demographic>) getHibernateTemplate().find(q, afterDatetimeExclusive)
            : (List<Demographic>) getHibernateTemplate().find(q);

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

    /*
     * @Override
     * public void saveOrUpdate(Demographic demographic) {
     * getHibernateTemplate().saveOrUpdate(demographic);
     * }
     */
}
