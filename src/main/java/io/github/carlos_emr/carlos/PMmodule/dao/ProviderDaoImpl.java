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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.commn.dao.ProviderFacilityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderFacility;
import io.github.carlos_emr.carlos.commn.model.ProviderFacilityPK;
import io.github.carlos_emr.carlos.config.CacheConfig;
import io.github.carlos_emr.carlos.dao.AbstractJpaDao;
import io.github.carlos_emr.carlos.provider.dto.ProviderSummaryDTO;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.model.security.SecProvider;
import io.github.carlos_emr.carlos.utility.JpqlQueryHelper;

@SuppressWarnings("unchecked")
@Transactional
public class ProviderDaoImpl extends AbstractJpaDao implements ProviderDao {


    private static Logger log = MiscUtils.getLogger();

    public boolean providerExists(String providerNo) {
        return entityManager().find(Provider.class, providerNo) != null;
    }

    @Override
    public Provider getProvider(String providerNo) {
        if (providerNo == null || providerNo.length() <= 0) {
            return null;
        }

        Provider provider = entityManager().find(Provider.class, providerNo);

        if (log.isDebugEnabled()) {
            log.debug("getProvider: providerNo=" + providerNo + ",found=" + (provider != null));
        }

        return provider;
    }

    @Cacheable(value = CacheConfig.PROVIDER_NAMES, key = "'name:' + #providerNo",
               condition = "#providerNo != null && !#providerNo.isEmpty()",
               unless = "#result == null || #result.isEmpty()")
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

    @Cacheable(value = CacheConfig.PROVIDER_NAMES, key = "'nameLastFirst:' + #providerNo",
               condition = "#providerNo != null && !#providerNo.isEmpty()",
               unless = "#result == null || #result.isEmpty()")
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

        List<Provider> rs = (List<Provider>) JpqlQueryHelper.find(entityManager(),
                "FROM  Provider p ORDER BY p.lastName");

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
        return (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, params);
    }

    @Override
    public List<Provider> getProviderFromFirstLastName(String firstname, String lastname) {
        firstname = firstname.trim();
        lastname = lastname.trim();
        String s = "From Provider p where p.firstName=?1 and p.lastName=?2";
        return (List<Provider>) JpqlQueryHelper.find(entityManager(), s, firstname, lastname);
    }

    @Override
    public List<Provider> getProviderLikeFirstLastName(String firstname, String lastname) {
        firstname = firstname.trim();
        lastname = lastname.trim();
        String s = "From Provider p where p.firstName like ?1 and p.lastName like ?2";
        return (List<Provider>) JpqlQueryHelper.find(entityManager(), s, firstname, lastname);
    }

    @Override
    public List<Provider> getActiveProviderLikeFirstLastName(String firstname, String lastname) {
        firstname = firstname.trim();
        lastname = lastname.trim();
        String s = "From Provider p where p.firstName like ?1 and p.lastName like ?2 and p.status='1'";
        return (List<Provider>) JpqlQueryHelper.find(entityManager(), s, firstname, lastname);
    }

    @Override
    public List<SecProvider> getActiveProviders(Integer programId) {
        String sSQL = "FROM  SecProvider p where p.status='1' and p.providerNo in " +
                "(select sr.providerNo from secUserRole sr, LstOrgcd o " +
                " where o.code = 'P' || ?1 " +
                " and o.codecsv  like '%' || sr.orgcd || ',%' " +
                " and not (sr.orgcd like 'R%' or sr.orgcd like 'O%'))" +
                " ORDER BY p.lastName";
        return (List<SecProvider>) JpqlQueryHelper.find(entityManager(), sSQL, programId);
    }

    @Override
    public List<Provider> getActiveProviders(String facilityId, String programId) {
        ArrayList<Object> paramList = new ArrayList<Object>();

        String sSQL;
        List<Provider> rs;
        if (programId != null && "0".equals(programId) == false) {
            sSQL = "FROM  Provider p where p.status='1' and p.providerNo in "
                    + "(select c.providerNo from ProgramProvider c where c.programId =?1) ORDER BY p.lastName";
            try {
                rs = (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, Long.valueOf(programId));
            } catch (NumberFormatException e) {
                // Caller asked for providers in a specific program but passed a non-numeric id.
                // Return empty rather than silently widening the result to all active providers.
                log.warn("getActiveProviders: non-numeric programId '{}', returning empty list",
                        LogSanitizer.sanitize(programId));
                return Collections.emptyList();
            }
        } else if (facilityId != null && "0".equals(facilityId) == false) {
            sSQL = "FROM  Provider p where p.status='1' and p.providerNo in "
                    + "(select c.providerNo from ProgramProvider c where c.programId in "
                    + "(select a.id from Program a where a.facilityId=?1)) ORDER BY p.lastName";
            // JS 2192700 - string facilityId seems to be throwing class cast
            // exception
            Integer intFacilityId = Integer.valueOf(facilityId);
            rs = (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, intFacilityId);
        } else {
            sSQL = "FROM  Provider p where p.status='1' ORDER BY p.lastName";
            rs = (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL);
        }

        return rs;
    }

    @Cacheable(value = CacheConfig.ACTIVE_PROVIDERS, key = "'filter:true'")
    @Override
    public List<Provider> getActiveProviders() {

        List<Provider> rs = (List<Provider>) JpqlQueryHelper.find(entityManager(),
                "FROM  Provider p where p.status='1' AND p.providerNo NOT LIKE '-%'  ORDER BY p.lastName");

        if (log.isDebugEnabled()) {
            log.debug("getProviders: # of results=" + rs.size());
        }
        return Collections.unmodifiableList(new ArrayList<>(rs));
    }

    @Cacheable(value = CacheConfig.ACTIVE_PROVIDERS, key = "'filter:' + #filterOutSystemAndImportedProviders")
    @Override
    public List<Provider> getActiveProviders(boolean filterOutSystemAndImportedProviders) {

        List<Provider> rs = null;

        if (!filterOutSystemAndImportedProviders) {
            rs = (List<Provider>) JpqlQueryHelper.find(entityManager(),
                    "FROM  Provider p where p.status='1' ORDER BY p.lastName");
        } else {
            rs = (List<Provider>) JpqlQueryHelper.find(entityManager(),
                    "FROM  Provider p where p.status='1' AND p.providerNo NOT LIKE '-%' ORDER BY p.lastName");
        }

        if (log.isDebugEnabled()) {
            log.debug("getProviders: # of results=" + rs.size());
        }
        return Collections.unmodifiableList(new ArrayList<>(rs));
    }

    @Override
    public List<Provider> getActiveProvidersByRole(String role) {
        if (role == null) return Collections.emptyList();
        // Uses Secuserrole (model.security) — the identity-PK mapping that matches the actual
        // secUserRole table schema. The PMmodule SecUserRole composite-key mapping is
        // intentionally absent from the test EMF and does not reflect the production DB.
        String sSQL = "select p FROM Provider p, Secuserrole s where p.providerNo = s.providerNo and p.status='1' " +
        "and s.roleName = ?1 order by p.lastName, p.firstName";
        List<Provider> rs = (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, role);

        if (log.isDebugEnabled()) {
            log.debug("getActiveProvidersByRole: # of results=" + rs.size());
        }
        return rs;
    }

    @Override
    public List<Provider> getDoctorsWithOhip() {
        return (List<Provider>) JpqlQueryHelper.find(entityManager(),
                "FROM Provider p " +
                        "WHERE p.providerType = 'doctor' " +
                        "AND p.status = '1' " +
                        "AND p.ohipNo IS NOT NULL " +
                        "ORDER BY p.lastName, p.firstName");
    }

    @Override
    public List<Provider> getBillableProviders() {
        List<Provider> rs = (List<Provider>) JpqlQueryHelper.find(entityManager(),
                "FROM Provider p where p.ohipNo != '' and p.status = '1' order by p.lastName");
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
        String sSQL = "FROM Provider p where (p.ohipNo <> '' or p.rmaNo <> ''  or p.billingNo <> '' or p.hsoNo <> '') " +
                "and p.status = '1' and p.providerNo not like ?1 order by p.lastName";
        return (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, loggedInInfo.getLoggedInProviderNo());
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Provider> getBillableProvidersInBC() {
        String sSQL = "FROM Provider p where (p.ohipNo <> '' or p.rmaNo <> ''  or p.billingNo <> '' or p.hsoNo <> '') " +
        "and p.status = '1' order by p.lastName";
        List<Provider> rs = (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL);
        return rs;
    }

    @Override
    public List<Provider> getProviders(boolean active) {
        String hql = "FROM Provider p where p.status = ?1 order by p.lastName";
        return (List<Provider>) JpqlQueryHelper.find(entityManager(), hql, active ? "1" : "0");
    }

    @Override
    public List<Provider> getActiveProviders(String providerNo, Integer shelterId) {
        String sql;
        ArrayList<Object> paramList = new ArrayList<Object>();
        if (shelterId == null || shelterId.intValue() == 0) {
            sql = "FROM  Provider p where p.status='1'" +
                    " and p.providerNo in (select sr.providerNo from Secuserrole sr " +
                    " where sr.orgcd in (select o.code from LstOrgcd o, Secuserrole srb " +
                    " where o.codecsv  like '%' || srb.orgcd || ',%' and srb.providerNo =?1))" +
                    " ORDER BY p.lastName";
            paramList.add(providerNo);
        } else {
            String shelterPattern = "%S" + shelterId + ",%";
            sql = "FROM  Provider p where p.status='1'" +
                    " and p.providerNo in (select sr.providerNo from Secuserrole sr " +
                    " where sr.orgcd in (select o.code from LstOrgcd o, Secuserrole srb " +
                    " where o.codecsv like ?1 and o.codecsv like '%' || srb.orgcd || ',%' and srb.providerNo =?2))" +
                    " ORDER BY p.lastName";
            paramList.add(shelterPattern);
            paramList.add(providerNo);
        }

        Object params[] = paramList.toArray(new Object[paramList.size()]);
        List<Provider> rs = (List<Provider>) JpqlQueryHelper.find(entityManager(), sql, params);

        if (log.isDebugEnabled()) {
            log.debug("getProviders: # of results=" + rs.size());
        }
        return rs;
    }

    @Override
    public List<Provider> getActiveProvider(String providerNo) {

        String sql = "FROM Provider p where p.status='1' and p.providerNo =?1";
        List<Provider> rs = (List<Provider>) JpqlQueryHelper.find(entityManager(), sql, providerNo);

        if (log.isDebugEnabled()) {
            log.debug("getProvider: # of results=" + rs.size());
        }
        return rs;
    }

    @Override
    public List<Provider> search(String name) {
        String hql = "FROM Provider p WHERE p.firstName LIKE ?1 OR p.lastName LIKE ?2 ORDER BY p.providerNo";
        List<Provider> results = (List<Provider>) JpqlQueryHelper.find(entityManager(), hql, name + "%", name + "%");

        if (log.isDebugEnabled()) {
            log.debug("search: # of results=" + results.size());
        }
        return results;
    }

    @Override
    public List<Provider> getProvidersByTypeWithNonEmptyOhipNo(String type) {
        String sSQL = "from Provider p where p.providerType = ?1 and p.ohipNo <> ''";
        List<Provider> results = (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, type);
        return results;
    }

    @Override
    public List<Provider> getProvidersByType(String type) {

        String sSQL = "from Provider p where p.providerType = ?1";
        List<Provider> results = (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, type);

        if (log.isDebugEnabled()) {
            log.debug("getProvidersByType: type=" + type + ",# of results="
                    + results.size());
        }

        return results;
    }

    @Override
    public List<Provider> getProvidersByTypePattern(String typePattern) {

        String sSQL = "from Provider p where p.providerType like ?1";
        List<Provider> results = (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, typePattern);
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

    @SuppressWarnings("unchecked")
    @Override
    public List<Integer> getFacilityIds(String provider_no) {
        Query query = entityManager().createNativeQuery(
                "SELECT facility_id FROM provider_facility, Facility WHERE Facility.id = provider_facility.facility_id AND Facility.disabled = 0 AND provider_no = :providerNo");
        query.setParameter("providerNo", provider_no);
        return (List<Integer>) query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    /**
     * Retrieves a list of provider IDs associated with the specified facility ID.
     */
    public List<String> getProviderIds(int facilityId) {
        Query query = entityManager().createNativeQuery(
                "SELECT provider_no FROM provider_facility WHERE facility_id = :facilityId");
        query.setParameter("facilityId", facilityId);
        return (List<String>) query.getResultList();
    }

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.PROVIDER_NAMES,             allEntries = true),
        @CacheEvict(value = CacheConfig.ACTIVE_PROVIDERS,           allEntries = true),
        @CacheEvict(value = CacheConfig.ACTIVE_PROVIDER_SUMMARIES,  allEntries = true)
    })
    @Override
    public void updateProvider(Provider provider) {
        entityManager().merge(provider);
    }

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.PROVIDER_NAMES,             allEntries = true),
        @CacheEvict(value = CacheConfig.ACTIVE_PROVIDERS,           allEntries = true),
        @CacheEvict(value = CacheConfig.ACTIVE_PROVIDER_SUMMARIES,  allEntries = true)
    })
    @Override
    public void saveProvider(Provider provider) {
        entityManager().persist(provider);
    }

    @Override
    public Provider getProviderByPractitionerNo(String practitionerNo) {
        if (practitionerNo == null || practitionerNo.length() <= 0) {
            return null;
        }

        String sSQL = "From Provider p where p.practitionerNo=?1";
        List<Provider> providerList = (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, practitionerNo);

        if (providerList.size() > 1) {
            log.warn("Found more than 1 providers with practitionerNo=" + practitionerNo);
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
        List<Provider> providerList = (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, params);

        if (providerList.size() > 1) {
            log.warn("Found more than 1 providers with practitionerNo=" + practitionerNo);
        }
        if (providerList.size() > 0)
            return providerList.get(0);

        return null;
    }

    @Override
    public List<String> getUniqueTeams() {

        List<String> providerList = (List<String>) JpqlQueryHelper.find(entityManager(),
                "select distinct p.team From Provider p");

        return providerList;
    }

    @Override
    public List<Provider> getBillableProvidersOnTeam(Provider p) {
        String team = p.getTeam();
        if (team == null) return Collections.emptyList();
        String sSQL = "from Provider p where p.status='1' and p.ohipNo!='' and p.team=?1 order by p.lastName, p.firstName";
        List<Provider> providers = (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, team);

        return providers;
    }

    @Override
    public List<Provider> getBillableProvidersByOHIPNo(String ohipNo) {
        if (ohipNo == null || ohipNo.length() <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from Provider p where p.ohipNo like ?1 order by p.lastName, p.firstName";
        List<Provider> providers = (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, ohipNo);

        if (providers.size() > 1) {
            log.warn("Found more than 1 providers with the given ohipNo");
        }
        if (providers.isEmpty())
            return null;
        else
            return providers;
    }

    @Override
    public List<Provider> getProvidersWithNonEmptyOhip(LoggedInInfo loggedInInfo) {
        String sSQL = "FROM Provider p WHERE p.ohipNo != '' and p.providerNo not like ?1 order by p.lastName, p.firstName";
        return (List<Provider>) JpqlQueryHelper.find(entityManager(), sSQL, loggedInInfo.getLoggedInProviderNo());
    }

    /**
     * Gets all providers with non-empty OHIP number ordered by last,then first name
     *
     * @return Returns the all found providers
     */
    @Override
    public List<Provider> getProvidersWithNonEmptyOhip() {
        return (List<Provider>) JpqlQueryHelper.find(entityManager(),
                "FROM Provider p WHERE p.ohipNo != '' order by p.lastName, p.firstName");
    }

    @Override
    public List<Provider> getCurrentTeamProviders(String providerNo) {
        String hql = "SELECT p FROM Provider p WHERE p.status='1' and p.ohipNo != '' AND (p.providerNo=?1 or p.team=(SELECT p2.team FROM Provider p2 where p2.providerNo=?2)) ORDER BY p.lastName, p.firstName";

        return (List<Provider>) JpqlQueryHelper.find(entityManager(), hql, providerNo, providerNo);
    }

    @Override
    public List<String> getActiveTeams() {
        List<String> providerList = (List<String>) JpqlQueryHelper.find(entityManager(),
                "select distinct p.team From Provider p where p.status = '1' and p.team != '' order by p.team");
        return providerList;
    }

    @SuppressWarnings("unchecked")
    @NativeSql({"provider", "providersite"})
    @Override
    /**
     * Retrieves a list of active teams associated with a given provider number.
     */
    public List<String> getActiveTeamsViaSites(String providerNo) {
        Query query = entityManager().createNativeQuery(
                "SELECT DISTINCT team FROM provider p INNER JOIN providersite s ON s.provider_no = p.provider_no WHERE s.site_id IN (SELECT site_id FROM providersite WHERE provider_no = :providerNo) ORDER BY team");
        query.setParameter("providerNo", providerNo);
        return (List<String>) query.getResultList();
    }

    @Override
    public List<Provider> getProviderByPatientId(Integer patientId) {
        String hql = "SELECT p FROM Provider p, Demographic d WHERE d.providerNo = p.providerNo AND d.demographicNo = ?1";
        return (List<Provider>) JpqlQueryHelper.find(entityManager(), hql, patientId);
    }

    @Override
    public List<Provider> getDoctorsWithNonEmptyCredentials() {
        String sql = "FROM Provider p WHERE p.providerType = 'doctor' " +
                "AND p.status='1' " +
                "AND p.ohipNo IS NOT NULL " +
                "AND p.ohipNo != '' " +
                "ORDER BY p.lastName, p.firstName";
        return (List<Provider>) JpqlQueryHelper.find(entityManager(), sql);
    }

    @Override
    public List<Provider> getProvidersWithNonEmptyCredentials() {
        String sql = "FROM Provider p WHERE p.status='1' " +
                "AND p.ohipNo IS NOT NULL " +
                "AND p.ohipNo != '' " +
                "ORDER BY p.lastName, p.firstName";
        return (List<Provider>) JpqlQueryHelper.find(entityManager(), sql);
    }

    @Override
    public List<String> getProvidersInTeam(String teamName) {
        if (teamName == null) return Collections.emptyList();
        String sSQL = "select distinct p.providerNo from Provider p  where p.team = ?1";
        List<String> providerList = (List<String>) JpqlQueryHelper.find(entityManager(), sSQL, teamName);
        return providerList;
    }

    @Override
    public List<Object[]> getDistinctProviders() {
        List<Object[]> providerList = (List<Object[]>) JpqlQueryHelper.find(entityManager(),
                "select distinct p.providerNo, p.providerType from Provider p ORDER BY p.lastName");
        return providerList;
    }

    @Override
    public List<String> getRecordsAddedAndUpdatedSinceTime(Date date) {
        String sSQL = "select distinct p.providerNo From Provider p where p.lastUpdateDate > ?1 ";
        @SuppressWarnings("unchecked")
        List<String> providers = (List<String>) JpqlQueryHelper.find(entityManager(), sSQL, date);

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
                sqlCommand = sqlCommand + " where x.lastName like :ln AND x.firstName like :fn";
            } else {
                sqlCommand = sqlCommand + " where x.lastName like :ln";
            }

        }

        TypedQuery<Provider> q = entityManager().createQuery(sqlCommand, Provider.class);
        if (searchString != null) {
            q.setParameter("ln", "%" + searchString.split(",")[0] + "%");
            if (searchString.indexOf(",") != -1 && searchString.split(",").length > 1
                    && searchString.split(",")[1].length() > 0) {
                q.setParameter("fn", "%" + searchString.split(",")[1] + "%");
            }
        }
        q.setFirstResult(startIndex);
        q.setMaxResults(itemsToReturn);
        return q.getResultList();
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
        String sqlCommand = "select x from Provider x WHERE x.status = :status ";

        if (term != null && term.length() > 0) {
            sqlCommand += "AND (x.lastName like :term  OR x.firstName like :term) ";
        }

        sqlCommand += " ORDER BY x.lastName,x.firstName";

        TypedQuery<Provider> q = entityManager().createQuery(sqlCommand, Provider.class);
        q.setParameter("status", active ? "1" : "0");
        if (term != null && term.length() > 0) {
            q.setParameter("term", term + "%");
        }
        q.setFirstResult(startIndex);
        q.setMaxResults(itemsToReturn);
        return q.getResultList();
    }

    @SuppressWarnings("unchecked")
    @NativeSql({"provider", "appointment"})
    @Override
    /**
     * Retrieves a list of provider numbers with appointments on the specified date.
     */
    public List<String> getProviderNosWithAppointmentsOnDate(Date appointmentDate) {
        Query query = entityManager().createNativeQuery(
                "SELECT p.provider_no FROM provider p WHERE p.provider_no IN (SELECT DISTINCT a.provider_no FROM appointment a WHERE a.appointment_date = :appointmentDate) AND p.status = '1'");
        query.setParameter("appointmentDate", new java.sql.Date(appointmentDate.getTime()));
        return (List<String>) query.getResultList();
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
        TypedQuery<Provider> query = entityManager().createQuery(
                "FROM Provider p WHERE p.providerNo IN (:providerNumbers)", Provider.class);
        query.setParameter("providerNumbers", providerNumbers);
        return query.getResultList();
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

    // --- DTO projection methods ---

    private static final String ACTIVE_PROVIDER_SUMMARIES_HQL =
            "SELECT NEW io.github.carlos_emr.carlos.provider.dto.providerSummaryDTO(p.providerNo, p.lastName, p.firstName, p.specialty, p.status, p.team) FROM Provider p WHERE p.status = '1' AND p.providerNo NOT LIKE '-%' ORDER BY p.lastName, p.firstName";

    private static final String PROVIDER_SUMMARY_BY_ID_HQL =
            "SELECT NEW io.github.carlos_emr.carlos.provider.dto.providerSummaryDTO(p.providerNo, p.lastName, p.firstName, p.specialty, p.status, p.team) FROM Provider p WHERE p.providerNo = :providerNo";

    private static final String PROVIDER_SUMMARIES_BY_IDS_HQL =
            "SELECT NEW io.github.carlos_emr.carlos.provider.dto.providerSummaryDTO(p.providerNo, p.lastName, p.firstName, p.specialty, p.status, p.team) FROM Provider p WHERE p.providerNo IN (:providerNumbers)";

    @Cacheable(value = CacheConfig.ACTIVE_PROVIDER_SUMMARIES)
    @Override
    public List<ProviderSummaryDTO> getActiveProviderSummaries() {
        TypedQuery<ProviderSummaryDTO> query = entityManager().createQuery(
                ACTIVE_PROVIDER_SUMMARIES_HQL, ProviderSummaryDTO.class);
        return Collections.unmodifiableList(new ArrayList<>(query.getResultList()));
    }

    @Override
    public ProviderSummaryDTO getProviderSummary(String providerNo) {
        if (providerNo == null || providerNo.isEmpty()) {
            return null;
        }
        TypedQuery<ProviderSummaryDTO> query = entityManager().createQuery(
                PROVIDER_SUMMARY_BY_ID_HQL, ProviderSummaryDTO.class);
        query.setParameter("providerNo", providerNo);
        query.setMaxResults(1);
        List<ProviderSummaryDTO> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public Map<String, ProviderSummaryDTO> getProviderSummariesByIds(Collection<String> providerNumbers) {
        Map<String, ProviderSummaryDTO> map = new HashMap<>();
        if (providerNumbers == null || providerNumbers.isEmpty()) {
            return map;
        }
        TypedQuery<ProviderSummaryDTO> query = entityManager().createQuery(
                PROVIDER_SUMMARIES_BY_IDS_HQL, ProviderSummaryDTO.class);
        query.setParameter("providerNumbers", providerNumbers);
        for (ProviderSummaryDTO dto : query.getResultList()) {
            map.put(dto.getProviderNo(), dto);
        }
        return map;
    }
}
