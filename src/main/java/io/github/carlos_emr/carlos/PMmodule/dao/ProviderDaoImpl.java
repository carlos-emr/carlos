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

package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.criterion.Expression;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.commn.dao.ProviderFacilityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderFacility;
import io.github.carlos_emr.carlos.commn.model.ProviderFacilityPK;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import io.github.carlos_emr.OscarProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.SessionFactory;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.model.security.SecProvider;
import io.github.carlos_emr.carlos.utility.HqlQueryHelper;

@SuppressWarnings("unchecked")
@Transactional
public class ProviderDaoImpl extends HibernateDaoSupport implements ProviderDao {


    private static Logger log = MiscUtils.getLogger();

    @Autowired
    public void setSessionFactoryOverride(SessionFactory sessionFactory) {
        log.info("Setting session factory in ProviderDaoImpl");
        if (sessionFactory == null) {
            log.error("SessionFactory is null!");
        } else {
            log.info("SessionFactory is successfully set.");
        }
        super.setSessionFactory(sessionFactory);
    }

    public boolean providerExists(String providerNo) {
        return getHibernateTemplate().get(Provider.class, providerNo) != null;
    }

    @Override
    public Provider getProvider(String providerNo) {
        if (providerNo == null || providerNo.length() <= 0) {
            return null;
        }

        Provider provider = getHibernateTemplate().get(Provider.class, providerNo);

        if (log.isDebugEnabled()) {
            log.debug("getProvider: providerNo=" + providerNo + ",found=" + (provider != null));
        }

        return provider;
    }

    @Override
    public String getProviderName(String providerNo) {

        String providerName = "";
        Provider provider = getProvider(providerNo);

        if (provider != null) {
            if (provider.getFirstName() != null) {
                providerName = provider.getFirstName() + " ";
            }

            if (provider.getLastName() != null) {
                providerName += provider.getLastName();
            }

            if (log.isDebugEnabled()) {
                log.debug("getProviderName: providerNo=" + providerNo + ",result=" + providerName);
            }
        }

        return providerName;
    }

    @Override
    public String getProviderNameLastFirst(String providerNo) {
        if (providerNo == null || providerNo.length() <= 0) {
            throw new IllegalArgumentException();
        }

        String providerName = "";
        Provider provider = getProvider(providerNo);

        if (provider != null) {
            if (provider.getLastName() != null) {
                providerName = provider.getLastName() + ", ";
            }

            if (provider.getFirstName() != null) {
                providerName += provider.getFirstName();
            }

            if (log.isDebugEnabled()) {
                log.debug("getProviderNameLastFirst: providerNo=" + providerNo + ",result=" + providerName);
            }
        }

        return providerName;
    }

    @Override
    public List<Provider> getProviders() {

        List<Provider> rs = (List<Provider>) HqlQueryHelper.find(currentSession(),
                "FROM  Provider p ORDER BY p.LastName");

        if (log.isDebugEnabled()) {
            log.debug("getProviders: # of results=" + rs.size());
        }
        return rs;
    }

    @Override
    public List<Provider> getProviders(String[] providers) {
        String sSQL = "FROM Provider p WHERE p.providerNumber IN (:providers)";
        Map<String, Object> params = new HashMap<>();
        params.put("providers", Arrays.asList(providers));
        return (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, params);
    }

    @Override
    public List<Provider> getProviderFromFirstLastName(String firstname, String lastname) {
        firstname = firstname.trim();
        lastname = lastname.trim();
        String s = "From Provider p where p.FirstName=?1 and p.LastName=?2";
        return (List<Provider>) HqlQueryHelper.find(currentSession(), s, firstname, lastname);
    }

    @Override
    public List<Provider> getProviderLikeFirstLastName(String firstname, String lastname) {
        firstname = firstname.trim();
        lastname = lastname.trim();
        String s = "From Provider p where p.FirstName like ?1 and p.LastName like ?2";
        return (List<Provider>) HqlQueryHelper.find(currentSession(), s, firstname, lastname);
    }

    @Override
    public List<Provider> getActiveProviderLikeFirstLastName(String firstname, String lastname) {
        firstname = firstname.trim();
        lastname = lastname.trim();
        String s = "From Provider p where p.FirstName like ?1 and p.LastName like ?2 and p.Status='1'";
        return (List<Provider>) HqlQueryHelper.find(currentSession(), s, firstname, lastname);
    }

    @Override
    public List<SecProvider> getActiveProviders(Integer programId) {
        String sSQL = "FROM  SecProvider p where p.status='1' and p.providerNo in " +
                "(select sr.providerNo from secUserRole sr, LstOrgcd o " +
                " where o.code = 'P' || ?1 " +
                " and o.codecsv  like '%' || sr.orgcd || ',%' " +
                " and not (sr.orgcd like 'R%' or sr.orgcd like 'O%'))" +
                " ORDER BY p.lastName";
        return (List<SecProvider>) HqlQueryHelper.find(currentSession(), sSQL, programId);
    }

    @Override
    public List<Provider> getActiveProviders(String facilityId, String programId) {
        ArrayList<Object> paramList = new ArrayList<Object>();

        String sSQL;
        List<Provider> rs;
        if (programId != null && "0".equals(programId) == false) {
            sSQL = "FROM  Provider p where p.Status='1' and p.ProviderNo in "
                    + "(select c.ProviderNo from ProgramProvider c where c.ProgramId =?1) ORDER BY p.LastName";
            rs = (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, Long.valueOf(programId));
        } else if (facilityId != null && "0".equals(facilityId) == false) {
            sSQL = "FROM  Provider p where p.Status='1' and p.ProviderNo in "
                    + "(select c.ProviderNo from ProgramProvider c where c.ProgramId in "
                    + "(select a.id from Program a where a.facilityId=?1)) ORDER BY p.LastName";
            // JS 2192700 - string facilityId seems to be throwing class cast
            // exception
            Integer intFacilityId = Integer.valueOf(facilityId);
            rs = (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, intFacilityId);
        } else {
            sSQL = "FROM  Provider p where p.Status='1' ORDER BY p.LastName";
            rs = (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL);
        }

        return rs;
    }

    @Override
    public List<Provider> getActiveProviders() {

        List<Provider> rs = (List<Provider>) HqlQueryHelper.find(currentSession(),
                "FROM  Provider p where p.Status='1' AND p.ProviderNo NOT LIKE '-%'  ORDER BY p.LastName");

        if (log.isDebugEnabled()) {
            log.debug("getProviders: # of results=" + rs.size());
        }
        return rs;
    }

    @Override
    public List<Provider> getActiveProviders(boolean filterOutSystemAndImportedProviders) {

        List<Provider> rs = null;

        if (!filterOutSystemAndImportedProviders) {
            rs = (List<Provider>) HqlQueryHelper.find(currentSession(),
                    "FROM  Provider p where p.Status='1' ORDER BY p.LastName");
        } else {
            rs = (List<Provider>) HqlQueryHelper.find(currentSession(),
                    "FROM  Provider p where p.Status='1' AND p.ProviderNo NOT LIKE '-%' ORDER BY p.LastName");
        }

        if (log.isDebugEnabled()) {
            log.debug("getProviders: # of results=" + rs.size());
        }
        return rs;
    }

    @Override
    public List<Provider> getActiveProvidersByRole(String role) {
        if (role == null) return Collections.emptyList();
        String sSQL = "select p FROM Provider p, SecUserRole s where p.ProviderNo = s.ProviderNo and p.Status='1' " +
        "and s.RoleName = ?1 order by p.LastName, p.FirstName";
        List<Provider> rs = (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, role);

        if (log.isDebugEnabled()) {
            log.debug("getActiveProvidersByRole: # of results=" + rs.size());
        }
        return rs;
    }

    @Override
    public List<Provider> getDoctorsWithOhip() {
        return (List<Provider>) HqlQueryHelper.find(currentSession(),
                "FROM Provider p " +
                        "WHERE p.ProviderType = 'doctor' " +
                        "AND p.Status = '1' " +
                        "AND p.OhipNo IS NOT NULL " +
                        "ORDER BY p.LastName, p.FirstName");
    }

    @Override
    public List<Provider> getBillableProviders() {
        List<Provider> rs = (List<Provider>) HqlQueryHelper.find(currentSession(),
                "FROM Provider p where p.OhipNo != '' and p.Status = '1' order by p.LastName");
        return rs;
    }

    /**
     * Returns all billable providers in BC, excluding the currently logged-in provider.
     *
     * <p>Billable providers are those with at least one of: OHIP number, RMA number,
     * billing number, or HSO number, and with active status.</p>
     *
     * @param loggedInInfo LoggedInInfo the session context of the currently logged-in provider to exclude
     * @return List&lt;Provider&gt; list of active billable BC providers excluding the logged-in provider,
     *         ordered by last name
     */
    @Override
    public List<Provider> getBillableProvidersInBC(LoggedInInfo loggedInInfo) {
        String sSQL = "FROM Provider p where (p.OhipNo <> '' or p.RmaNo <> ''  or p.BillingNo <> '' or p.HsoNo <> '') " +
                "and p.Status = '1' and p.ProviderNo not like ?1 order by p.LastName";
        return (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, loggedInInfo.getLoggedInProviderNo());
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Provider> getBillableProvidersInBC() {
        String sSQL = "FROM Provider p where (p.OhipNo <> '' or p.RmaNo <> ''  or p.BillingNo <> '' or p.HsoNo <> '') " +
        "and p.Status = '1' order by p.LastName";
        List<Provider> rs = (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL);
        return rs;
    }

    @Override
    public List<Provider> getProviders(boolean active) {
        String hql = "FROM Provider p where p.Status = ?1 order by p.LastName";
        return (List<Provider>) HqlQueryHelper.find(currentSession(), hql, active ? "1" : "0");
    }

    @Override
    public List<Provider> getActiveProviders(String providerNo, Integer shelterId) {
        String sql;
        ArrayList<Object> paramList = new ArrayList<Object>();
        if (shelterId == null || shelterId.intValue() == 0) {
            sql = "FROM  Provider p where p.Status='1'" +
                    " and p.ProviderNo in (select sr.providerNo from Secuserrole sr " +
                    " where sr.orgcd in (select o.code from LstOrgcd o, Secuserrole srb " +
                    " where o.codecsv  like '%' || srb.orgcd || ',%' and srb.providerNo =?1))" +
                    " ORDER BY p.LastName";
            paramList.add(providerNo);
        } else {
            String shelterPattern = "%S" + shelterId + ",%";
            sql = "FROM  Provider p where p.Status='1'" +
                    " and p.ProviderNo in (select sr.providerNo from Secuserrole sr " +
                    " where sr.orgcd in (select o.code from LstOrgcd o, Secuserrole srb " +
                    " where o.codecsv like ?1 and o.codecsv like '%' || srb.orgcd || ',%' and srb.providerNo =?2))" +
                    " ORDER BY p.LastName";
            paramList.add(shelterPattern);
            paramList.add(providerNo);
        }

        Object params[] = paramList.toArray(new Object[paramList.size()]);
        List<Provider> rs = (List<Provider>) HqlQueryHelper.find(currentSession(), sql, params);

        if (log.isDebugEnabled()) {
            log.debug("getProviders: # of results=" + rs.size());
        }
        return rs;
    }

    @Override
    public List<Provider> getActiveProvider(String providerNo) {

        String sql = "FROM Provider p where p.Status='1' and p.ProviderNo =?1";
        List<Provider> rs = (List<Provider>) HqlQueryHelper.find(currentSession(), sql, providerNo);

        if (log.isDebugEnabled()) {
            log.debug("getProvider: # of results=" + rs.size());
        }
        return rs;
    }

    @Override
    public List<Provider> search(String name) {
        boolean isOracle = OscarProperties.getInstance().getDbType().equals(
                "oracle");
        Session session = currentSession();

        Criteria c = session.createCriteria(Provider.class);
        if (isOracle) {
            c.add(Restrictions.or(Expression.ilike("FirstName", name + "%"),
                    Expression.ilike("LastName", name + "%")));
        } else {
            c.add(Restrictions.or(Expression.like("FirstName", name + "%"),
                    Expression.like("LastName", name + "%")));
        }
        c.addOrder(Order.asc("ProviderNo"));

        List<Provider> results = new ArrayList<Provider>();

        try {
            results = c.list();
        } finally {
            // no-op: currentSession() lifecycle managed by Spring transaction
        }

        if (log.isDebugEnabled()) {
            log.debug("search: # of results=" + results.size());
        }
        return results;
    }

    @Override
    public List<Provider> getProvidersByTypeWithNonEmptyOhipNo(String type) {
        String sSQL = "from Provider p where p.ProviderType = ?1 and p.OhipNo <> ''";
        List<Provider> results = (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, type);
        return results;
    }

    @Override
    public List<Provider> getProvidersByType(String type) {

        String sSQL = "from Provider p where p.ProviderType = ?1";
        List<Provider> results = (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, type);

        if (log.isDebugEnabled()) {
            log.debug("getProvidersByType: type=" + type + ",# of results="
                    + results.size());
        }

        return results;
    }

    @Override
    public List<Provider> getProvidersByTypePattern(String typePattern) {

        String sSQL = "from Provider p where p.ProviderType like ?1";
        List<Provider> results = (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, typePattern);
        return results;
    }


    @Override
    public void addProviderToFacility(String provider_no, int facilityId) {
        try {
            ProviderFacility pf = new ProviderFacility();
            pf.setId(new ProviderFacilityPK());
            pf.getId().setProviderNo(provider_no);
            pf.getId().setFacilityId(facilityId);
            ProviderFacilityDao pfDao = SpringUtils.getBean(ProviderFacilityDao.class);
            pfDao.persist(pf);
        } catch (RuntimeException e) {
            // chances are it's a duplicate unique entry exception so it's safe
            // to ignore.
            // this is still unexpected because duplicate calls shouldn't be
            // made
            log.warn("Unexpected exception occurred.", e);
        }
    }

    @Override
    public void removeProviderFromFacility(String provider_no,
                                           int facilityId) {
        ProviderFacilityDao dao = SpringUtils.getBean(ProviderFacilityDao.class);
        for (ProviderFacility p : dao.findByProviderNoAndFacilityId(provider_no, facilityId)) {
            dao.remove(p.getId());
        }
    }

    @Override
    public List<Integer> getFacilityIds(String provider_no) {
        Session session = currentSession();
        try {
            SQLQuery query = session.createSQLQuery(
                    "select facility_id from provider_facility,Facility where Facility.id=provider_facility.facility_id and Facility.disabled=0 and provider_no=:providerNo");
            query.setParameter("providerNo", provider_no);
            List<Integer> results = query.list();
            return results;
        } finally {
            // no-op: currentSession() lifecycle managed by Spring transaction
        }
    }

    @Override
    /**
     * Retrieves a list of provider IDs associated with the specified facility ID.
     */
    public List<String> getProviderIds(int facilityId) {
        Session session = currentSession();
        try {
            SQLQuery query = session
                    .createSQLQuery("select provider_no from provider_facility where facility_id=:facilityId");
            query.setParameter("facilityId", facilityId);
            List<String> results = query.list();
            return results;
        } finally {
            // no-op: currentSession() lifecycle managed by Spring transaction
        }

    }

    @Override
    public void updateProvider(Provider provider) {
        this.getHibernateTemplate().update(provider);
    }

    @Override
    public void saveProvider(Provider provider) {
        this.getHibernateTemplate().save(provider);
    }

    @Override
    public Provider getProviderByPractitionerNo(String practitionerNo) {
        if (practitionerNo == null || practitionerNo.length() <= 0) {
            return null;
        }

        String sSQL = "From Provider p where p.practitionerNo=?1";
        List<Provider> providerList = (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, practitionerNo);

        if (providerList.size() > 1) {
            logger.warn("Found more than 1 providers with practitionerNo=" + practitionerNo);
        }
        if (providerList.size() > 0)
            return providerList.get(0);

        return null;
    }

    @Override
    public Provider getProviderByPractitionerNo(String practitionerNoType, String practitionerNo) {
        return getProviderByPractitionerNo(new String[]{practitionerNoType}, practitionerNo);
    }

    @Override
    public Provider getProviderByPractitionerNo(String[] practitionerNoTypes, String practitionerNo) {
        if (practitionerNoTypes == null || practitionerNoTypes.length <= 0) {
            throw new IllegalArgumentException();
        }
        if (practitionerNo == null || practitionerNo.length() <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "From Provider p where p.practitionerNoType IN (:types) AND p.practitionerNo = :practNo";
        Map<String, Object> params = new HashMap<>();
        params.put("types", Arrays.asList(practitionerNoTypes));
        params.put("practNo", practitionerNo);
        List<Provider> providerList = (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, params);

        if (providerList.size() > 1) {
            logger.warn("Found more than 1 providers with practitionerNo=" + practitionerNo);
        }
        if (providerList.size() > 0)
            return providerList.get(0);

        return null;
    }

    @Override
    public List<String> getUniqueTeams() {

        List<String> providerList = (List<String>) HqlQueryHelper.find(currentSession(),
                "select distinct p.Team From Provider p");

        return providerList;
    }

    @Override
    public List<Provider> getBillableProvidersOnTeam(Provider p) {
        String team = p.getTeam();
        if (team == null) return Collections.emptyList();
        String sSQL = "from Provider p where status='1' and ohip_no!='' and p.Team=?1 order by last_name, first_name";
        List<Provider> providers = (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, team);

        return providers;
    }

    @Override
    public List<Provider> getBillableProvidersByOHIPNo(String ohipNo) {
        if (ohipNo == null || ohipNo.length() <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from Provider p where ohip_no like ?1 order by last_name, first_name";
        List<Provider> providers = (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, ohipNo);

        if (providers.size() > 1) {
            logger.warn("Found more than 1 providers with ohipNo=" + ohipNo);
        }
        if (providers.isEmpty())
            return null;
        else
            return providers;
    }

    @Override
    public List<Provider> getProvidersWithNonEmptyOhip(LoggedInInfo loggedInInfo) {
        String sSQL = "FROM Provider WHERE ohip_no != '' and ProviderNo not like ?1 order by last_name, first_name";
        return (List<Provider>) HqlQueryHelper.find(currentSession(), sSQL, loggedInInfo.getLoggedInProviderNo());
    }

    /**
     * Gets all providers with non-empty OHIP number ordered by last,then first name
     *
     * @return Returns the all found providers
     */
    @Override
    public List<Provider> getProvidersWithNonEmptyOhip() {
        return (List<Provider>) HqlQueryHelper.find(currentSession(),
                "FROM Provider WHERE ohip_no != '' order by last_name, first_name");
    }

    @Override
    public List<Provider> getCurrentTeamProviders(String providerNo) {
        String hql = "SELECT p FROM Provider p WHERE p.Status='1' and p.OhipNo != '' AND (p.ProviderNo=?1 or team=(SELECT p2.Team FROM Provider p2 where p2.ProviderNo=?2)) ORDER BY p.LastName, p.FirstName";

        return (List<Provider>) HqlQueryHelper.find(currentSession(), hql, providerNo, providerNo);
    }

    @Override
    public List<String> getActiveTeams() {
        List<String> providerList = (List<String>) HqlQueryHelper.find(currentSession(),
                "select distinct p.Team From Provider p where p.Status = '1' and p.Team != '' order by p.Team");
        return providerList;
    }

    @NativeSql({"provider", "providersite"})
    @Override
    /**
     * Retrieves a list of active teams associated with a given provider number.
     */
    public List<String> getActiveTeamsViaSites(String providerNo) {
        Session session = currentSession();
        try {
            // providersite is not mapped in hibernate - this can be rewritten w.o.
            // subselect with a cross product IHMO
            String sql = "select distinct team from provider p inner join providersite s on s.provider_no = p.provider_no where s.site_id in (select site_id from providersite where provider_no = :providerNo) order by team";
            SQLQuery query = session.createSQLQuery(sql);
            query.setParameter("providerNo", providerNo);
            return query.list();
        } finally {
            // no-op: currentSession() lifecycle managed by Spring transaction
        }
    }

    @Override
    public List<Provider> getProviderByPatientId(Integer patientId) {
        String hql = "SELECT p FROM Provider p, Demographic d WHERE d.ProviderNo = p.ProviderNo AND d.DemographicNo = ?1";
        return (List<Provider>) HqlQueryHelper.find(currentSession(), hql, patientId);
    }

    @Override
    public List<Provider> getDoctorsWithNonEmptyCredentials() {
        String sql = "FROM Provider p WHERE p.ProviderType = 'doctor' " +
                "AND p.Status='1' " +
                "AND p.OhipNo IS NOT NULL " +
                "AND p.OhipNo != '' " +
                "ORDER BY p.LastName, p.FirstName";
        return (List<Provider>) HqlQueryHelper.find(currentSession(), sql);
    }

    @Override
    public List<Provider> getProvidersWithNonEmptyCredentials() {
        String sql = "FROM Provider p WHERE p.Status='1' " +
                "AND p.OhipNo IS NOT NULL " +
                "AND p.OhipNo != '' " +
                "ORDER BY p.LastName, p.FirstName";
        return (List<Provider>) HqlQueryHelper.find(currentSession(), sql);
    }

    @Override
    public List<String> getProvidersInTeam(String teamName) {
        if (teamName == null) return Collections.emptyList();
        String sSQL = "select distinct p.ProviderNo from Provider p  where p.Team = ?1";
        List<String> providerList = (List<String>) HqlQueryHelper.find(currentSession(), sSQL, teamName);
        return providerList;
    }

    @Override
    public List<Object[]> getDistinctProviders() {
        List<Object[]> providerList = (List<Object[]>) HqlQueryHelper.find(currentSession(),
                "select distinct p.ProviderNo, p.ProviderType from Provider p ORDER BY p.LastName");
        return providerList;
    }

    @Override
    public List<String> getRecordsAddedAndUpdatedSinceTime(Date date) {
        String sSQL = "select distinct p.ProviderNo From Provider p where p.lastUpdateDate > ?1 ";
        @SuppressWarnings("unchecked")
        List<String> providers = (List<String>) HqlQueryHelper.find(currentSession(), sSQL, date);

        return providers;
    }

    @SuppressWarnings("unchecked")
    @Override
    /**
     * Searches for providers by their names based on the given search string.
     *
     * This method constructs a SQL query to find providers whose last and optionally first names match the
     * specified search string. If the search string contains a comma, it splits the string to extract the last
     * and first names. The method then executes the query with pagination parameters defined by startIndex
     * and itemsToReturn, returning a list of matching providers.
     *
     * @param searchString the string containing the last name and optionally the first name, separated by a comma
     * @param startIndex the index of the first result to return
     * @param itemsToReturn the maximum number of results to return
     */
    public List<Provider> searchProviderByNamesString(String searchString, int startIndex, int itemsToReturn) {
        String sqlCommand = "select x from Provider x";
        if (searchString != null) {
            if (searchString.indexOf(",") != -1 && searchString.split(",").length > 1
                    && searchString.split(",")[1].length() > 0) {
                sqlCommand = sqlCommand + " where x.LastName like :ln AND x.FirstName like :fn";
            } else {
                sqlCommand = sqlCommand + " where x.LastName like :ln";
            }

        }

        Session session = currentSession();
        try {
            Query q = session.createQuery(sqlCommand);
            if (searchString != null) {
                q.setParameter("ln", "%" + searchString.split(",")[0] + "%");
                if (searchString.indexOf(",") != -1 && searchString.split(",").length > 1
                        && searchString.split(",")[1].length() > 0) {
                    q.setParameter("fn", "%" + searchString.split(",")[1] + "%");

                }
            }
            q.setFirstResult(startIndex);
            q.setMaxResults(itemsToReturn);
            return (q.list());
        } finally {
            // no-op: currentSession() lifecycle managed by Spring transaction
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    /**
     * Searches for active providers based on the given search term.
     *
     * This method constructs a SQL query to retrieve providers whose status matches the specified
     * active state. If a search term is provided, it filters the results based on the last or first
     * name of the providers. The results are ordered by last name and first name, and pagination is
     * applied using the startIndex and itemsToReturn parameters.
     *
     * @param term the search term to filter providers by their last or first name
     * @param active the status of the providers to search for
     * @param startIndex the index of the first result to return
     * @param itemsToReturn the maximum number of results to return
     */
    public List<Provider> search(String term, boolean active, int startIndex, int itemsToReturn) {
        String sqlCommand = "select x from Provider x WHERE x.Status = :status ";

        if (term != null && term.length() > 0) {
            sqlCommand += "AND (x.LastName like :term  OR x.FirstName like :term) ";
        }

        sqlCommand += " ORDER BY x.LastName,x.FirstName";

        Session session = currentSession();
        try {
            Query q = session.createQuery(sqlCommand);

            q.setString("status", active ? "1" : "0");
            if (term != null && term.length() > 0) {
                q.setString("term", term + "%");
            }

            q.setFirstResult(startIndex);
            q.setMaxResults(itemsToReturn);
            return (q.list());
        } finally {
            // no-op: currentSession() lifecycle managed by Spring transaction
        }
    }

    @NativeSql({"provider", "appointment"})
    @Override
    /**
     * Retrieves a list of provider numbers with appointments on the specified date.
     */
    public List<String> getProviderNosWithAppointmentsOnDate(Date appointmentDate) {
        Session session = currentSession();
        try {
            String sql = "SELECT p.provider_no FROM provider p WHERE p.provider_no IN (SELECT DISTINCT a.provider_no FROM appointment a WHERE a.appointment_date = :appointmentDate) AND p.Status='1'";
            SQLQuery query = session.createSQLQuery(sql);
            query.setParameter("appointmentDate", new java.sql.Date(appointmentDate.getTime()));
            return query.list();
        } finally {
            // no-op: currentSession() lifecycle managed by Spring transaction
        }
    }

    /**
     * Gets a list of providers numbers based on the provided list of providers
     * numbers
     *
     * @param providerNumbers The list of providers numbers to get the related
     *                        objects for
     * @return A list of providers
     */
    @Override
    public List<Provider> getProvidersByIds(List<String> providerNumbers) {
        Session session = currentSession();
        String sql = "FROM Provider p WHERE p.ProviderNo IN (:providerNumbers)";
        Query query = session.createQuery(sql);
        query.setParameterList("providerNumbers", providerNumbers);

        return query.list();
    }

    /**
     * Gets a map of providers names with the providers number as the map key based on
     * the provided list of providers numbers
     *
     * @param providerNumbers A list of providers numbers to get the name map for
     * @return A map of providers names with their related providers number as the key
     */
    @Override
    public Map<String, String> getProviderNamesByIdsAsMap(List<String> providerNumbers) {
        Map<String, String> providerNameMap = new HashMap<>();
        List<Provider> providers = getProvidersByIds(providerNumbers);

        for (Provider provider : providers) {
            providerNameMap.put(provider.getProviderNo(), provider.getFullName());
        }

        return providerNameMap;
    }
}
