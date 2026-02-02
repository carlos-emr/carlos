/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.commn.model.ProviderSitePK;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class SiteDaoTest extends DaoTestFixtures {

    protected SiteDao dao = SpringUtils.getBean(SiteDao.class);

    public SiteDaoTest() {
    }


    @Before
    public void before() throws Exception {
        SchemaUtils.restoreTable("site", "providersite", "provider", "mygroup", "appointment");
    }

    @Test
    public void testCreate() throws Exception {
        Site entity = new Site();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setStatus((byte) 1);
        dao.persist(entity);
        assertNotNull(entity.getId());
        assertNotNull(dao.find(entity.getId()));
    }

    @Test
    public void testManyToManyRelationship() throws Exception {
        Site entity = new Site();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setStatus((byte) 1);
        entity.setShortName("name1");
        dao.persist(entity);
        assertNotNull(entity.getId());
        Integer siteId1 = entity.getId();

        entity = new Site();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setStatus((byte) 1);
        entity.setShortName("name2");
        dao.persist(entity);
        assertNotNull(entity.getId());
        Integer siteId2 = entity.getId();

        //create a providers
        Provider p = new Provider();
        p.setProviderNo("000001");
        p.setLastName("Smith");
        p.setFirstName("John");
        p.setProviderType("doctor");
        p.setDob(new Date());
        p.setLastUpdateUser("999998");
        p.setLastUpdateDate(new Date());
        p.setSex("M");
        p.setSpecialty("");
        ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
        providerDao.saveProvider(p);

        ProviderSiteDao providerSiteDao = SpringUtils.getBean(ProviderSiteDao.class);
        ProviderSite ps = new ProviderSite();
        ps.setId(new ProviderSitePK());
        ps.getId().setProviderNo(p.getProviderNo());
        ps.getId().setSiteId(siteId1);
        providerSiteDao.persist(ps);

        ps = new ProviderSite();
        ps.setId(new ProviderSitePK());
        ps.getId().setProviderNo(p.getProviderNo());
        ps.getId().setSiteId(siteId2);
        providerSiteDao.persist(ps);

        Site s = dao.find(siteId1);
        assertNotNull(s.getProviders());
        assertEquals(s.getProviders().size(), 1);
    }


}
