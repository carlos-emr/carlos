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

package ca.openosp.openo.PMmodule.dao;

/**
 * Data Access Object implementation for Provider entities in the PMmodule.
 * <p>
 * This DAO handles all database operations for Provider entities including
 * queries for active providers, provider searches, provider teams, and
 * provider credentials. It supports multi-jurisdictional provider data
 * (BC, ON) and integrates with the program management and facility systems.
 * <p>
 * This implementation has been migrated from HibernateDaoSupport to direct
 * SessionFactory injection to support Spring 6 / Jakarta EE migration.
 * 
 * @since OpenO EMR 2025
 */

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Expression;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import ca.openosp.OscarProperties;
import ca.openosp.openo.commn.NativeSql;
import ca.openosp.openo.commn.dao.ProviderFacilityDao;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.ProviderFacility;
import ca.openosp.openo.commn.model.ProviderFacilityPK;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.model.security.SecProvider;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

@SuppressWarnings("unchecked")
@Transactional
public class ProviderDaoImpl implements ProviderDao {

    private static Logger log = MiscUtils.getLogger();

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session from the SessionFactory.
     * <p>
     * This method retrieves the session bound to the current transaction context.
     * It should only be called within an active transaction.
     * 
     * @return the current Hibernate Session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Checks if a provider exists in the database.
     * 
     * @param providerNo the provider number to check
     * @return true if the provider exists, false otherwise
     */
    public boolean providerExists(String providerNo) {
        return getSession().get(Provider.class, providerNo) != null;
    }

    /**
     * Retrieves a provider by their provider number.
     * 
     * @param providerNo the provider number to look up
     * @return the Provider entity, or null if not found
     */
    @Override
    public Provider getProvider(String providerNo) {
        if (providerNo == null || providerNo.length() <= 0) {
            return null;
        }

        Provider provider = getSession().get(Provider.class, providerNo);

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

    /**
     * Gets all providers ordered by last name.
     * 
     * @return list of all providers
     */
    @Override
    public List<Provider> getProviders() {

        List<Provider> rs = getSession().createQuery(
                "FROM  Provider p ORDER BY p.LastName").list();

        if (log.isDebugEnabled()) {
            log.debug("getProviders: # of results=" + rs.size());
        }
        return rs;
    }

    /**
     * Gets providers by their provider numbers.
     * 
     * @param providers array of provider numbers
     * @return list of matching providers
     */
    @Override
    public List<Provider> getProviders(String[] providers) {
        String sSQL = "FROM Provider p WHERE p.providerNumber IN (:providers)";
        Query query = getSession().createQuery(sSQL);
        query.setParameterList("providers", providers);
        return query.list();
    }

    /**
     * Gets providers by first and last name (exact match).
     * 
     * @param firstname the first name to match
     * @param lastname the last name to match
     * @return list of matching providers
     */
    @Override
    public List<Provider> getProviderFromFirstLastName(String firstname, String lastname) {
        firstname = firstname.trim();
        lastname = lastname.trim();
        String s = "From Provider p where p.FirstName=:firstname and p.LastName=:lastname";
        Query query = getSession().createQuery(s);
        query.setParameter("firstname", firstname);
        query.setParameter("lastname", lastname);
        return query.list();
    }

    /**
     * Gets providers by partial match on first and last name.
     * 
     * @param firstname the first name pattern to match (using LIKE)
     * @param lastname the last name pattern to match (using LIKE)
     * @return list of matching providers
     */
    @Override
    public List<Provider> getProviderLikeFirstLastName(String firstname, String lastname) {
        firstname = firstname.trim();
        lastname = lastname.trim();
        String s = "From Provider p where p.FirstName like :firstname and p.LastName like :lastname";
        Query query = getSession().createQuery(s);
        query.setParameter("firstname", firstname);
        query.setParameter("lastname", lastname);
        return query.list();
    }

    /**
     * Gets active providers by partial match on first and last name.
     * 
     * @param firstname the first name pattern to match (using LIKE)
     * @param lastname the last name pattern to match (using LIKE)
     * @return list of matching active providers
     */
    @Override
    public List<Provider> getActiveProviderLikeFirstLastName(String firstname, String lastname) {
        firstname = firstname.trim();
        lastname = lastname.trim();
        String s = "From Provider p where p.FirstName like :firstname and p.LastName like :lastname and p.Status='1'";
        Query query = getSession().createQuery(s);
        query.setParameter("firstname", firstname);
        query.setParameter("lastname", lastname);
        return query.list();
    }

    /**
     * Gets active providers for a specific program.
     * 
     * @param programId the program ID
     * @return list of active SecProvider entities for the program
     */
    @Override
    public List<SecProvider> getActiveProviders(Integer programId) {
        String sSQL = "FROM  SecProvider p where p.status='1' and p.providerNo in " +
                "(select sr.providerNo from secUserRole sr, LstOrgcd o " +
                " where o.code = 'P' || :programId " +
                " and o.codecsv  like '%' || sr.orgcd || ',%' " +
                " and not (sr.orgcd like 'R%' or sr.orgcd like 'O%'))" +
                " ORDER BY p.lastName";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("programId", programId);
        return query.list();
    }

    /**
     * Gets active providers for a facility and/or program.
     * 
     * @param facilityId the facility ID (optional, use "0" or null to ignore)
     * @param programId the program ID (optional, use "0" or null to ignore)
     * @return list of active providers matching the criteria
     */
    @Override
    public List<Provider> getActiveProviders(String facilityId, String programId) {
        ArrayList<Object> paramList = new ArrayList<Object>();

        String sSQL;
        List<Provider> rs;
        if (programId != null && "0".equals(programId) == false) {
            sSQL = "FROM  Provider p where p.Status='1' and p.ProviderNo in "
                    + "(select c.ProviderNo from ProgramProvider c where c.ProgramId =:programId) ORDER BY p.LastName";
            Query query = getSession().createQuery(sSQL);
            query.setParameter("programId", Long.valueOf(programId));
            rs = query.list();
        } else if (facilityId != null && "0".equals(facilityId) == false) {
            sSQL = "FROM  Provider p where p.Status='1' and p.ProviderNo in "
                    + "(select c.ProviderNo from ProgramProvider c where c.ProgramId in "
                    + "(select a.id from Program a where a.facilityId=:facilityId)) ORDER BY p.LastName";
            // JS 2192700 - string facilityId seems to be throwing class cast
            // exception
            Integer intFacilityId = Integer.valueOf(facilityId);
            Query query = getSession().createQuery(sSQL);
            query.setParameter("facilityId", intFacilityId);
            rs = query.list();
        } else {
            sSQL = "FROM  Provider p where p.Status='1' ORDER BY p.LastName";
            rs = getSession().createQuery(sSQL).list();
        }

        return rs;
    }

    /**
     * Gets all active providers excluding those with negative provider numbers.
     * <p>
     * Negative provider numbers are typically used for system or imported providers.
     * 
     * @return list of active providers
     */
    @Override
    public List<Provider> getActiveProviders() {

        List<Provider> rs = getSession().createQuery(
                "FROM  Provider p where p.Status='1' AND p.ProviderNo NOT LIKE '-%'  ORDER BY p.LastName").list();

        if (log.isDebugEnabled()) {
            log.debug("getProviders: # of results=" + rs.size());
        }
        return rs;
    }

    /**
     * Gets active providers with optional filtering of system and imported providers.
     * 
     * @param filterOutSystemAndImportedProviders if true, excludes providers with negative provider numbers
     * @return list of active providers
     */
    @Override
    public List<Provider> getActiveProviders(boolean filterOutSystemAndImportedProviders) {

        List<Provider> rs = null;

        if (!filterOutSystemAndImportedProviders) {
            rs = getSession().createQuery(
                    "FROM  Provider p where p.Status='1' ORDER BY p.LastName").list();
        } else {
            rs = getSession().createQuery(
                    "FROM  Provider p where p.Status='1' AND p.ProviderNo > -1 ORDER BY p.LastName").list();
        }

        if (log.isDebugEnabled()) {
            log.debug("getProviders: # of results=" + rs.size());
        }
        return rs;
    }

    /**
     * Gets active providers by their security role.
     * 
     * @param role the security role name to filter by
     * @return list of active providers with the specified role
     */
    @Override
    public List<Provider> getActiveProvidersByRole(String role) {

        String sSQL = "select p FROM Provider p, SecUserRole s where p.ProviderNo = s.ProviderNo and p.Status='1' " +
        "and s.RoleName = :role order by p.LastName, p.FirstName";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("role", role);
        List<Provider> rs = query.list();

        if (log.isDebugEnabled()) {
            log.debug("getActiveProvidersByRole: # of results=" + rs.size());
        }
        return rs;
    }

    /**
     * Gets all doctors (provider type = 'doctor') with OHIP numbers.
     * 
     * @return list of doctors with non-null OHIP numbers, ordered by name
     */
    @Override
    public List<Provider> getDoctorsWithOhip() {
        return getSession().createQuery(
                "FROM Provider p " +
                        "WHERE p.ProviderType = 'doctor' " +
                        "AND p.Status = '1' " +
                        "AND p.OhipNo IS NOT NULL " +
                        "ORDER BY p.LastName, p.FirstName").list();
    }

    /**
     * Gets all billable providers (those with non-empty OHIP numbers).
     * 
     * @return list of active providers with OHIP numbers
     */
    @Override
    public List<Provider> getBillableProviders() {
        List<Provider> rs = getSession().createQuery(
                "FROM Provider p where p.OhipNo != '' and p.Status = '1' order by p.LastName").list();
        return rs;
    }

    /**
     * Gets billable providers in BC excluding the logged-in provider.
     * <p>
     * BC providers are identified by having one or more of: OhipNo, RmaNo, BillingNo, or HsoNo.
     * 
     * @param loggedInInfo the logged-in user info (used to exclude current provider)
     * @return list of active billable providers in BC
     */
    @Override
    public List<Provider> getBillableProvidersInBC(LoggedInInfo loggedInInfo) {
        String sSQL = "FROM Provider p where (p.OhipNo <> '' or p.RmaNo <> ''  or p.BillingNo <> '' or p.HsoNo <> '') " +
                "and p.Status = '1' and p.ProviderNo not like :providerNo order by p.LastName";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("providerNo", loggedInInfo.getLoggedInProviderNo());
        return query.list();
    }

    /**
     * Gets all billable providers in BC.
     * <p>
     * BC providers are identified by having one or more of: OhipNo, RmaNo, BillingNo, or HsoNo.
     * 
     * @return list of active billable providers in BC
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Provider> getBillableProvidersInBC() {
        String sSQL = "FROM Provider p where (p.OhipNo <> '' or p.RmaNo <> ''  or p.BillingNo <> '' or p.HsoNo <> '') " +
        "and p.Status = '1' order by p.LastName";
        List<Provider> rs = getSession().createQuery(sSQL).list();
        return rs;
    }

    /**
     * Gets providers by active status.
     * 
     * @param active true for active providers (status='1'), false for inactive (status='0')
     * @return list of providers matching the status
     */
    @Override
    public List<Provider> getProviders(boolean active) {

        List<Provider> rs = getSession().createQuery(
                "FROM  Provider p where p.Status='" + (active ? 1 : 0) + "' order by p.LastName").list();
        return rs;
    }

    /**
     * Gets active providers filtered by provider and shelter.
     * <p>
     * Returns providers who share organization codes with the given provider,
     * optionally filtered by shelter ID.
     * 
     * @param providerNo the provider number to match organization codes against
     * @param shelterId optional shelter ID filter (null or 0 to ignore)
     * @return list of active providers
     */
    @Override
    public List<Provider> getActiveProviders(String providerNo, Integer shelterId) {
        String sql;
        Query query;
        if (shelterId == null || shelterId.intValue() == 0) {
            sql = "FROM  Provider p where p.Status='1'" +
                    " and p.ProviderNo in (select sr.providerNo from Secuserrole sr " +
                    " where sr.orgcd in (select o.code from LstOrgcd o, Secuserrole srb " +
                    " where o.codecsv  like '%' || srb.orgcd || ',%' and srb.providerNo =:providerNo))" +
                    " ORDER BY p.LastName";
            query = getSession().createQuery(sql);
            query.setParameter("providerNo", providerNo);
        } else {
            sql = "FROM  Provider p where p.Status='1'" +
                    " and p.ProviderNo in (select sr.providerNo from Secuserrole sr " +
                    " where sr.orgcd in (select o.code from LstOrgcd o, Secuserrole srb " +
                    " where o.codecsv like '%S:shelterId,%' and o.codecsv like '%' || srb.orgcd || ',%' and srb.providerNo =:providerNo))" +
                    " ORDER BY p.LastName";
            query = getSession().createQuery(sql);
            query.setParameter("shelterId", shelterId);
            query.setParameter("providerNo", providerNo);
        }

        List<Provider> rs = query.list();

        if (log.isDebugEnabled()) {
            log.debug("getProviders: # of results=" + rs.size());
        }
        return rs;
    }

    /**
     * Gets active provider by provider number.
     * 
     * @param providerNo the provider number to look up
     * @return list containing the provider if found and active
     */
    @Override
    public List<Provider> getActiveProvider(String providerNo) {

        String sql = "FROM Provider p where p.Status='1' and p.ProviderNo =:providerNo";
        Query query = getSession().createQuery(sql);
        query.setParameter("providerNo", providerNo);
        List<Provider> rs = query.list();

        if (log.isDebugEnabled()) {
            log.debug("getProvider: # of results=" + rs.size());
        }
        return rs;
    }

    /**
     * Searches for providers by name (first name or last name).
     * <p>
     * Uses case-insensitive matching (ilike) for Oracle databases
     * and regular like matching for other databases.
     * 
     * @param name the name prefix to search for
     * @return list of matching providers ordered by provider number
     */
    @Override
    public List<Provider> search(String name) {
        boolean isOracle = OscarProperties.getInstance().getDbType().equals(
                "oracle");
        Session session = getSession();

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
            // Session is managed by Spring transaction, no manual close needed
        }

        if (log.isDebugEnabled()) {
            log.debug("search: # of results=" + results.size());
        }
        return results;
    }

    /**
     * Gets providers by type with non-empty OHIP numbers.
     * 
     * @param type the provider type (e.g., "doctor", "nurse")
     * @return list of providers of the specified type with OHIP numbers
     */
    @Override
    public List<Provider> getProvidersByTypeWithNonEmptyOhipNo(String type) {
        String sSQL = "from Provider p where p.ProviderType = :type and p.OhipNo <> ''";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("type", type);
        List<Provider> results = query.list();
        return results;
    }

    /**
     * Gets providers by provider type.
     * 
     * @param type the provider type (e.g., "doctor", "nurse")
     * @return list of providers of the specified type
     */
    @Override
    public List<Provider> getProvidersByType(String type) {

        String sSQL = "from Provider p where p.ProviderType = :type";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("type", type);
        List<Provider> results = query.list();

        if (log.isDebugEnabled()) {
            log.debug("getProvidersByType: type=" + type + ",# of results="
                    + results.size());
        }

        return results;
    }

    /**
     * Gets providers by provider type pattern.
     * 
     * @param typePattern the provider type pattern (supports SQL LIKE wildcards)
     * @return list of providers matching the type pattern
     */
    @Override
    public List<Provider> getProvidersByTypePattern(String typePattern) {

        String sSQL = "from Provider p where p.ProviderType like :typePattern";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("typePattern", typePattern);
        List<Provider> results = query.list();
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

    /**
     * Gets the facility IDs associated with a provider.
     * <p>
     * Only returns facilities that are not disabled.
     * 
     * @param provider_no the provider number
     * @return list of facility IDs
     */
    @Override
    public List<Integer> getFacilityIds(String provider_no) {
        Session session = getSession();
        try {
            SQLQuery query = session.createSQLQuery(
                    "select facility_id from provider_facility,Facility where Facility.id=provider_facility.facility_id and Facility.disabled=0 and provider_no=\'"
                            + provider_no + "\'");
            List<Integer> results = query.list();
            return results;
        } finally {
            // Session is managed by Spring transaction, no manual close needed
        }
    }

    /**
     * Gets the provider numbers associated with a facility.
     * 
     * @param facilityId the facility ID
     * @return list of provider numbers
     */
    @Override
    public List<String> getProviderIds(int facilityId) {
        Session session = getSession();
        try {
            SQLQuery query = session
                    .createSQLQuery("select provider_no from provider_facility where facility_id=" + facilityId);
            List<String> results = query.list();
            return results;
        } finally {
            // Session is managed by Spring transaction, no manual close needed
        }

    }

    /**
     * Updates an existing provider in the database.
     * 
     * @param provider the provider entity to update
     */
    @Override
    public void updateProvider(Provider provider) {
        getSession().update(provider);
    }

    /**
     * Saves a new provider to the database.
     * 
     * @param provider the provider entity to save
     */
    @Override
    public void saveProvider(Provider provider) {
        getSession().save(provider);
    }

    /**
     * Gets a provider by practitioner number.
     * 
     * @param practitionerNo the practitioner number to look up
     * @return the provider with the matching practitioner number, or null if not found
     */
    @Override
    public Provider getProviderByPractitionerNo(String practitionerNo) {
        if (practitionerNo == null || practitionerNo.length() <= 0) {
            return null;
        }

        String sSQL = "From Provider p where p.practitionerNo=:practitionerNo";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("practitionerNo", practitionerNo);
        List<Provider> providerList = query.list();

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

    /**
     * Gets a provider by practitioner number type and number.
     * 
     * @param practitionerNoTypes array of practitioner number types to match
     * @param practitionerNo the practitioner number to look up
     * @return the provider matching the criteria, or null if not found
     * @throws IllegalArgumentException if parameters are null or empty
     */
    @Override
    public Provider getProviderByPractitionerNo(String[] practitionerNoTypes, String practitionerNo) {
        if (practitionerNoTypes == null || practitionerNoTypes.length <= 0) {
            throw new IllegalArgumentException();
        }
        if (practitionerNo == null || practitionerNo.length() <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "From Provider p where p.practitionerNoType IN (:types) AND p.practitionerNo=:practitionerNo";
        Query query = getSession().createQuery(sSQL);
        query.setParameterList("types", practitionerNoTypes);
        query.setParameter("practitionerNo", practitionerNo);
        List<Provider> providerList = query.list();

        if (providerList.size() > 1) {
            logger.warn("Found more than 1 providers with practitionerNo=" + practitionerNo);
        }
        if (providerList.size() > 0)
            return providerList.get(0);

        return null;
    }

    /**
     * Gets all unique team names from providers.
     * 
     * @return list of distinct team names
     */
    @Override
    public List<String> getUniqueTeams() {

        List<String> providerList = getSession().createQuery(
                "select distinct p.Team From Provider p").list();

        return providerList;
    }

    /**
     * Gets billable providers on the same team as the given provider.
     * 
     * @param p the provider whose team to search
     * @return list of active providers with OHIP numbers on the same team
     */
    @Override
    public List<Provider> getBillableProvidersOnTeam(Provider p) {

        String sSQL = "from Provider p where status='1' and ohip_no!='' and p.Team=:team order by last_name, first_name";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("team", p.getTeam());
        List<Provider> providers = query.list();

        return providers;
    }

    /**
     * Gets billable providers by OHIP number.
     * 
     * @param ohipNo the OHIP number pattern to search for (supports LIKE)
     * @return list of providers with matching OHIP numbers, or null if none found
     * @throws IllegalArgumentException if ohipNo is null or empty
     */
    @Override
    public List<Provider> getBillableProvidersByOHIPNo(String ohipNo) {
        if (ohipNo == null || ohipNo.length() <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from Provider p where ohip_no like :ohipNo order by last_name, first_name";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("ohipNo", ohipNo);
        List<Provider> providers = query.list();

        if (providers.size() > 1) {
            logger.warn("Found more than 1 providers with ohipNo=" + ohipNo);
        }
        if (providers.isEmpty())
            return null;
        else
            return providers;
    }

    /**
     * Gets providers with non-empty OHIP numbers, excluding the logged-in provider.
     * 
     * @param loggedInInfo the logged-in user info (used to exclude current provider)
     * @return list of providers with OHIP numbers
     */
    @Override
    public List<Provider> getProvidersWithNonEmptyOhip(LoggedInInfo loggedInInfo) {
        String sSQL = "FROM Provider WHERE ohip_no != '' and ProviderNo not like :providerNo order by last_name, first_name";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("providerNo", loggedInInfo.getLoggedInProviderNo());
        return query.list();
    }

    /**
     * Gets all providers with non-empty OHIP numbers.
     * <p>
     * Results are ordered by last name, then first name.
     * 
     * @return list of all providers with OHIP numbers
     */
    @Override
    public List<Provider> getProvidersWithNonEmptyOhip() {
        return getSession().createQuery(
                "FROM Provider WHERE ohip_no != '' order by last_name, first_name").list();
    }

    /**
     * Gets current team providers for a given provider.
     * <p>
     * Returns active providers with OHIP numbers who are either the specified provider
     * or on the same team as the specified provider.
     * 
     * @param providerNo the provider number to find team members for
     * @return list of team providers including the specified provider
     */
    @Override
    public List<Provider> getCurrentTeamProviders(String providerNo) {
        String hql = "SELECT p FROM Provider p "
                + "WHERE p.Status='1' and p.OhipNo != '' "
                + "AND (p.ProviderNo='" + providerNo
                + "' or team=(SELECT p2.Team FROM Provider p2 where p2.ProviderNo='" + providerNo + "')) "
                + "ORDER BY p.LastName, p.FirstName";

        return getSession().createQuery(hql).list();
    }

    /**
     * Gets all active team names.
     * <p>
     * Returns distinct team names from active providers with non-empty team values.
     * 
     * @return list of active team names, sorted alphabetically
     */
    @Override
    public List<String> getActiveTeams() {
        List<String> providerList = getSession().createQuery(
                "select distinct p.Team From Provider p where p.Status = '1' and p.Team != '' order by p.Team").list();
        return providerList;
    }

    /**
     * Gets active teams via provider sites.
     * <p>
     * Returns distinct team names for providers who share sites with the given provider.
     * Note: The providersite table is not mapped in Hibernate.
     * 
     * @param providerNo the provider number to find teams for
     * @return list of team names
     */
    @NativeSql({"provider", "providersite"})
    @Override
    public List<String> getActiveTeamsViaSites(String providerNo) {
        Session session = getSession();
        try {
            // providersite is not mapped in hibernate - this can be rewritten w.o.
            // subselect with a cross product IHMO
            SQLQuery query = session.createSQLQuery(
                    "select distinct team from provider p inner join providersite s on s.provider_no = p.provider_no " +
                            " where s.site_id in (select site_id from providersite where provider_no = '" + providerNo
                            + "') order by team ");
            return query.list();
        } finally {
            // Session is managed by Spring transaction, no manual close needed
        }
    }

    /**
     * Gets providers associated with a patient (demographic).
     * 
     * @param patientId the demographic number (patient ID)
     * @return list of providers associated with the patient
     */
    @Override
    public List<Provider> getProviderByPatientId(Integer patientId) {
        String hql = "SELECT p FROM Provider p, Demographic d "
                + "WHERE d.ProviderNo = p.ProviderNo "
                + "AND d.DemographicNo = :patientId";
        Query query = getSession().createQuery(hql);
        query.setParameter("patientId", patientId);
        return query.list();
    }

    /**
     * Gets all active doctors with non-empty credentials (OHIP numbers).
     * 
     * @return list of active doctors with credentials
     */
    @Override
    public List<Provider> getDoctorsWithNonEmptyCredentials() {
        String sql = "FROM Provider p WHERE p.ProviderType = 'doctor' " +
                "AND p.Status='1' " +
                "AND p.OhipNo IS NOT NULL " +
                "AND p.OhipNo != '' " +
                "ORDER BY p.LastName, p.FirstName";
        return getSession().createQuery(sql).list();
    }

    /**
     * Gets all active providers with non-empty credentials (OHIP numbers).
     * 
     * @return list of active providers with credentials
     */
    @Override
    public List<Provider> getProvidersWithNonEmptyCredentials() {
        String sql = "FROM Provider p WHERE p.Status='1' " +
                "AND p.OhipNo IS NOT NULL " +
                "AND p.OhipNo != '' " +
                "ORDER BY p.LastName, p.FirstName";
        return getSession().createQuery(sql).list();
    }

    /**
     * Gets provider numbers for all providers in a team.
     * 
     * @param teamName the team name to search for
     * @return list of provider numbers in the specified team
     */
    @Override
    public List<String> getProvidersInTeam(String teamName) {
        String sSQL = "select distinct p.ProviderNo from Provider p  where p.Team = :teamName";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("teamName", teamName);
        List<String> providerList = query.list();
        return providerList;
    }

    /**
     * Gets distinct provider numbers and types.
     * <p>
     * Returns an array for each provider containing [providerNo, providerType].
     * 
     * @return list of Object arrays with provider number and type
     */
    @Override
    public List<Object[]> getDistinctProviders() {
        List<Object[]> providerList = getSession().createQuery(
                "select distinct p.ProviderNo, p.ProviderType from Provider p ORDER BY p.LastName").list();
        return providerList;
    }

    /**
     * Gets provider numbers for providers added or updated since a specific time.
     * <p>
     * Useful for incremental synchronization or change tracking.
     * 
     * @param date the cutoff date - returns providers updated after this date
     * @return list of provider numbers updated since the given date
     */
    @Override
    public List<String> getRecordsAddedAndUpdatedSinceTime(Date date) {
        String sSQL = "select distinct p.ProviderNo From Provider p where p.lastUpdateDate > :date ";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("date", date);
        @SuppressWarnings("unchecked")
        List<String> providers = query.list();

        return providers;
    }

    /**
     * Searches providers by name with pagination support.
     * <p>
     * Supports searching by last name only, or by both last name and first name
     * when the search string contains a comma separator.
     * 
     * @param searchString the search string (format: "lastName" or "lastName,firstName")
     * @param startIndex the starting index for pagination
     * @param itemsToReturn the maximum number of items to return
     * @return list of matching providers
     */
    @SuppressWarnings("unchecked")
    @Override
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

        Session session = getSession();
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
            // Session is managed by Spring transaction, no manual close needed
        }
    }

    /**
     * Searches for active or inactive providers by term with pagination.
     * <p>
     * Searches both first name and last name fields for partial matches.
     * Results are ordered by last name, then first name.
     * 
     * @param term the search term to match against provider names
     * @param active true to search active providers (status='1'), false for inactive
     * @param startIndex the starting index for pagination
     * @param itemsToReturn the maximum number of items to return
     * @return list of matching providers
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Provider> search(String term, boolean active, int startIndex, int itemsToReturn) {
        String sqlCommand = "select x from Provider x WHERE x.Status = :status ";

        if (term != null && term.length() > 0) {
            sqlCommand += "AND (x.LastName like :term  OR x.FirstName like :term) ";
        }

        sqlCommand += " ORDER BY x.LastName,x.FirstName";

        Session session = getSession();
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
            // Session is managed by Spring transaction, no manual close needed
        }
    }

    /**
     * Gets provider numbers who have appointments on a specific date.
     * <p>
     * Only returns active providers (status='1').
     * Note: The appointment table is not mapped in Hibernate.
     * 
     * @param appointmentDate the date to check for appointments
     * @return list of provider numbers with appointments on the given date
     */
    @NativeSql({"provider", "appointment"})
    @Override
    public List<String> getProviderNosWithAppointmentsOnDate(Date appointmentDate) {
        Session session = getSession();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        try {
            String sql = "SELECT p.provider_no FROM provider p WHERE p.provider_no IN (SELECT DISTINCT a.provider_no FROM appointment a WHERE a.appointment_date = '"
                    + sdf.format(appointmentDate) + "') " +
                    "AND p.Status='1'";
            SQLQuery query = session.createSQLQuery(sql);

            return query.list();
        } finally {
            // Session is managed by Spring transaction, no manual close needed
        }
    }

    /**
     * Gets OLIS HIC providers.
     * <p>
     * Returns providers who have a practitioner number and an OLIS identifier type
     * configured in their user properties.
     * 
     * @return list of providers with OLIS HIC configuration
     */
    @Override
    public List<Provider> getOlisHicProviders() {
        UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
        Session session = getSession();
        String sql = "FROM Provider p WHERE p.practitionerNo IS NOT NULL AND p.practitionerNo != ''";
        Query query = session.createQuery(sql);
        List<Provider> practitionerNoProviders = query.list();

        List<Provider> results = new ArrayList<Provider>();
        for (Provider practitionerNoProvider : practitionerNoProviders) {
            String olisType = userPropertyDAO.getStringValue(practitionerNoProvider.getProviderNo(),
                    UserProperty.OFFICIAL_OLIS_IDTYPE);
            if (olisType != null && !olisType.isEmpty()) {
                results.add(practitionerNoProvider);
            }
        }
        return results;
    }

    /**
     * Gets a provider by practitioner number and OLIS identifier type.
     * <p>
     * Validates that the provider's OLIS identifier type matches the requested type.
     * 
     * @param practitionerNo the practitioner number
     * @param olisIdentifierType the expected OLIS identifier type
     * @return the provider if found with matching OLIS type, null otherwise
     */
    @Override
    public Provider getProviderByPractitionerNoAndOlisType(String practitionerNo, String olisIdentifierType) {
        UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
        String sql = "FROM Provider p WHERE p.practitionerNo=:practitionerNo";
        Query query = getSession().createQuery(sql);
        query.setParameter("practitionerNo", practitionerNo);
        List<Provider> providers = query.list();

        if (!providers.isEmpty()) {
            Provider provider = providers.get(0);
            String olisType = userPropertyDAO.getStringValue(provider.getProviderNo(),
                    UserProperty.OFFICIAL_OLIS_IDTYPE);
            if (olisIdentifierType.equals(olisType)) {
                return providers.get(0);
            }
        }
        return null;
    }

    /**
     * Gets OLIS providers by their practitioner numbers.
     * 
     * @param practitionerNumbers list of practitioner numbers to search for
     * @return list of providers matching the practitioner numbers
     */
    @Override
    public List<Provider> getOlisProvidersByPractitionerNo(List<String> practitionerNumbers) {
        Session session = getSession();
        String sql = "FROM Provider p WHERE p.practitionerNo IN (:practitionerNumbers)";
        Query query = session.createQuery(sql);
        query.setParameterList("practitionerNumbers", practitionerNumbers);
        List<Provider> providers = query.list();
        return providers;
    }

    /**
     * Gets a list of providers by their provider numbers.
     * 
     * @param providerNumbers The list of provider numbers to get the related objects for
     * @return A list of providers
     */
    @Override
    public List<Provider> getProvidersByIds(List<String> providerNumbers) {
        Session session = getSession();
        String sql = "FROM Provider p WHERE p.ProviderNo IN (:providerNumbers)";
        Query query = session.createQuery(sql);
        query.setParameterList("providerNumbers", providerNumbers);

        List<Provider> providers = query.list();
        return providers;
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
