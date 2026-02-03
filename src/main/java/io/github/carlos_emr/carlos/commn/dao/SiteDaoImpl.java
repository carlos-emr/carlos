/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.stereotype.Repository;

/**
 * @author Victor Weng
 */
@Repository
public class SiteDaoImpl extends AbstractDaoImpl<Site> implements SiteDao {

    /**
     * Creates a new instance of UserPropertyDAO
     */
    public SiteDaoImpl() {
        super(Site.class);
    }

    @Override
    public void save(Site s) {
        boolean isUpdate = s.getSiteId() != null && s.getSiteId() > 0;
        if (isUpdate) {
            Site old = find(s.getSiteId());
            if (!old.getName().equals(s.getName())) {
                // site name changed, need to update all references as it serves as PK
                // so we need to update the tables that references to the site

                Query query = entityManager.createNativeQuery("update rschedule set avail_hour = replace(avail_hour, ?1, ?2) ");
                query.setParameter(1, ">" + old.getName() + "<");
                query.setParameter(2, ">" + s.getName() + "<");
                query.executeUpdate();

                query = entityManager.createNativeQuery("update scheduledate set reason = ?1 where reason = ?2");
                query.setParameter(1, s.getName());
                query.setParameter(2, old.getName());
                query.executeUpdate();

                query = entityManager.createNativeQuery("update appointment set location = ?1 where location = ?2");
                query.setParameter(1, s.getName());
                query.setParameter(2, old.getName());
                query.executeUpdate();

                query = entityManager.createNativeQuery("update billing_on_cheader1 set clinic = ?1 where clinic = ?2");
                query.setParameter(1, s.getName());
                query.setParameter(2, old.getName());
                query.executeUpdate();


            }
        }

        Set<Provider> providers = new HashSet<Provider>();
        ProviderSiteDao providerSiteDao = SpringUtils.getBean(ProviderSiteDao.class);
        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
        List<ProviderSite> psList = providerSiteDao.findBySiteId(s.getSiteId());
        for (ProviderSite ps : psList) {
            Provider p = providerDao.getProvider(ps.getId().getProviderNo());
            providers.add(p);
        }
        s.setProviders(providers);

        if (s.getSiteLogoId() != null && s.getSiteLogoId().intValue() == 0)
            s.setSiteLogoId(null);
        merge(s);

    }

    @Override
    public List<Site> getAllSites() {
        Query query = this.entityManager.createQuery("select s from Site s order by s.name");

        @SuppressWarnings("unchecked")
        List<Site> rs = query.getResultList();
        return rs;
    }

    @Override
    public List<Site> getAllActiveSites() {
        Query query = this.entityManager.createQuery("select s from Site s where s.status=1 order by s.name");

        @SuppressWarnings("unchecked")
        List<Site> rs = query.getResultList();

        return rs;
    }

    @Override
    public List<Site> getActiveSitesByProviderNo(String provider_no) {
        ProviderSiteDao providerSiteDao = SpringUtils.getBean(ProviderSiteDao.class);
        List<ProviderSite> pss = providerSiteDao.findByProviderNo(provider_no);

        List<Site> rs = new ArrayList<Site>();
        for (ProviderSite ps : pss) {
            Site site = find(ps.getId().getSiteId());
            if (site != null) {
                rs.add(site);
            }
        }

        Iterator<Site> it = rs.iterator();
        while (it.hasNext()) {
            Site site = it.next();
            // remove inactive sites
            if (site.getStatus() == 0)
                it.remove();
        }

        Collections.sort(rs, new Comparator<Site>() {
            public int compare(Site o1, Site o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        return rs;
    }

    @Override
    public Site getById(Integer id) {
        return find(id);
    }

    @Override
    public Site getByLocation(String location) {
        Query query = this.entityManager.createQuery("select s from Site s where s.name=?");
        query.setParameter(0, location);

        @SuppressWarnings("unchecked")
        List<Site> rs = query.getResultList();

        if (rs.size() > 0)
            return rs.get(0);
        else
            return null;
    }

    @Override
    public List<String> getGroupBySiteLocation(String location) {
        Query query = entityManager.createNativeQuery(
                "select distinct g.mygroup_no from mygroup g	" +
                        " inner join provider p on p.provider_no = g.provider_no and p.status = 1 " +
                        " inner join providersite ps on ps.provider_no = g.provider_no " +
                        " inner join site s on s.site_id = ps.site_id " +
                        " where  s.name = ?1 ");
        query.setParameter(1, location);

        @SuppressWarnings("unchecked")
        List<String> groupList = query.getResultList();


        return groupList;
    }

    @Override
    public List<String> getProviderNoBySiteLocation(String location) {

        Query query = entityManager.createNativeQuery(
                "select distinct p.provider_no	" +
                        " from provider p " +
                        " inner join providersite ps on ps.provider_no = p.provider_no " +
                        " inner join site s on s.site_id = ps.site_id " +
                        " where  s.name = ?1 ");
        query.setParameter(1, location);

        @SuppressWarnings("unchecked")
        List<String> pList = query.getResultList();


        return pList;
    }

    @Override
    public List<String> getProviderNoBySiteManagerProviderNo(String providerNo) {

        Query query = entityManager.createNativeQuery(
                "select distinct p.provider_no	" +
                        " from provider p " +
                        " inner join providersite ps on ps.provider_no = p.provider_no and p.status = 1" +
                        " where ps.site_id in (select site_id from providersite where provider_no = ?1)");
        query.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<String> pList = query.getResultList();

        return pList;
    }

    @Override
    public List<String> getGroupBySiteManagerProviderNo(String providerNo) {

        Query query = entityManager.createNativeQuery(
                "select distinct g.mygroup_no from mygroup g	" +
                        " inner join provider p on p.provider_no = g.provider_no and p.status = 1 " +
                        " inner join providersite ps on ps.provider_no = g.provider_no " +
                        " where ps.site_id in (select site_id from providersite where provider_no = ?1)");
        query.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<String> groupList = query.getResultList();


        return groupList;
    }

    @Override
    public Long site_searchmygroupcount(String myGroupNo, String siteName) {
        Query query = entityManager.createNativeQuery("select count(provider_no) from mygroup where mygroup_no=:groupno  and provider_no in (select ps.provider_no from providersite ps inner join site s on ps.site_id = s.site_id where s.name = :sitename)");
        query.setParameter(1, myGroupNo);
        query.setParameter(2, siteName);

        Long result = ((BigInteger) query.getSingleResult()).longValue();
        return result;
    }

    @Override
    public String getSiteNameByAppointmentNo(String appointmentNo) {

        Query query = entityManager.createNativeQuery("select location from appointment where appointment_no = ?1");
        query.setParameter(1, appointmentNo);

        @SuppressWarnings("unchecked")
        List<String> list = query.getResultList();
        if (list.size() > 0) {
            return list.get(0);
        }

        return "";
    }

    @Override
    public List<String> getGroupsBySiteProviderNo(String groupNo) {
        List<String> groupList = new ArrayList<String>();
        Query query = entityManager.createNativeQuery(
                "select distinct g.mygroup_no from mygroup g	" +
                        " inner join provider p on p.provider_no = g.provider_no and p.status = 1 " +
                        " inner join providersite ps on ps.provider_no = g.provider_no " +
                        " where ps.site_id in (select site_id from providersite where provider_no = ?1)");
        query.setParameter(1, groupNo);

        groupList = query.getResultList();
        return groupList;
    }

    @Override
    public List<String> getGroupsForAllSites() {
        List<String> groupList = new ArrayList<String>();
        Query query = entityManager.createNativeQuery(
                "select distinct g.mygroup_no from mygroup g	" +
                        " inner join provider p on p.provider_no = g.provider_no and p.status = 1 " +
                        " inner join providersite ps on ps.provider_no = g.provider_no");

        groupList = query.getResultList();
        return groupList;
    }

    @Override
    public Site findByName(String name) {
        Query query = entityManager.createQuery("select site from Site site where site.name = ?1");
        query.setParameter(1, name);
        try {
            return (Site) query.getSingleResult();
        } catch (Exception e) {
        }
        return null;
    }
}
 