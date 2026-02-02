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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicMerged;
import io.github.carlos_emr.carlos.commn.dao.DemographicMergedDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class DemographicMergedDaoTest extends DaoTestFixtures {

    protected DemographicMergedDao dao = SpringUtils.getBean(DemographicMergedDao.class);
    protected DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);

    @Before
    public void before() throws Exception {
        SchemaUtils.restoreTable("demographic_merged", "demographic");
    }

    @Test
    public void testCreate() throws Exception {
        // Creating a new demographic in this test to enforce foreign key addition of "demographic_id" in the "demographic_merged" table
        Demographic demo = new Demographic();
        EntityDataGenerator.generateTestDataForModelClass(demo);
        demo.setDemographicNo(null);
        demographicDao.save(demo);

        DemographicMerged entity = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setMergedTo(demo.getDemographicNo());
        dao.persist(entity);
        assertNotNull(entity.getId());
    }

    @Test
    public void testFindCurrentByMergedTo() throws Exception {
        int isNotDeleted = 0;
        int isDeleted = 1;

        // Creating new demographics in this test to enforce foreign key addition of "demographic_id" in the "demographic_merged" table
        Demographic demo1 = new Demographic();
        EntityDataGenerator.generateTestDataForModelClass(demo1);
        demo1.setDemographicNo(null);
        demographicDao.save(demo1);

        Demographic demo2 = new Demographic();
        EntityDataGenerator.generateTestDataForModelClass(demo2);
        demo2.setDemographicNo(null);
        demographicDao.save(demo2);

        int demoId1 = demo1.getDemographicNo();
        int demoId2 = demo2.getDemographicNo();

        DemographicMerged demoMerged1 = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(demoMerged1);
        demoMerged1.setMergedTo(demoId1);
        demoMerged1.setDeleted(isNotDeleted);
        dao.persist(demoMerged1);

        DemographicMerged demoMerged2 = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(demoMerged2);
        demoMerged2.setMergedTo(demoId2);
        demoMerged2.setDeleted(isNotDeleted);
        dao.persist(demoMerged2);

        DemographicMerged demoMerged3 = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(demoMerged3);
        demoMerged3.setMergedTo(demoId1);
        demoMerged3.setDeleted(isNotDeleted);
        dao.persist(demoMerged3);

        DemographicMerged demoMerged4 = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(demoMerged4);
        demoMerged4.setMergedTo(demoId1);
        demoMerged4.setDeleted(isDeleted);
        dao.persist(demoMerged4);

        List<DemographicMerged> expectedResult = new ArrayList<DemographicMerged>(Arrays.asList(demoMerged1, demoMerged3));
        List<DemographicMerged> result = dao.findCurrentByMergedTo(demoId1);
        Logger logger = MiscUtils.getLogger();
        if (result.size() != expectedResult.size()) {
            logger.warn("Array sizes do not match.");
            fail("Array sizes do not match.");
        }

        for (int i = 0; i < expectedResult.size(); i++) {
            if (!expectedResult.get(i).equals(result.get(i))) {
                logger.warn("Items do not match.");
                fail("Items do not match.");
            }
        }
        assertTrue(true);
    }

    @Test
    public void testFindCurrentByDemographicNo() throws Exception {
        int isNotDeleted = 0;
        int isDeleted = 1;

        // Creating new demographics in this test to enforce foreign key addition of "demographic_id" in the "demographic_merged" table
        Demographic demo1 = new Demographic();
        EntityDataGenerator.generateTestDataForModelClass(demo1);
        demo1.setDemographicNo(null);
        demographicDao.save(demo1);

        Demographic demo2 = new Demographic();
        EntityDataGenerator.generateTestDataForModelClass(demo2);
        demo2.setDemographicNo(null);
        demographicDao.save(demo2);

        int demoId1 = demo1.getDemographicNo();
        int demoId2 = demo2.getDemographicNo();

        DemographicMerged demoMerged1 = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(demoMerged1);
        demoMerged1.setMergedTo(demoId1);
        demoMerged1.setDemographicNo(demoId1);
        demoMerged1.setDeleted(isNotDeleted);
        dao.persist(demoMerged1);

        DemographicMerged demoMerged2 = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(demoMerged2);
        demoMerged2.setMergedTo(demoId2);
        demoMerged2.setDemographicNo(demoId2);
        demoMerged2.setDeleted(isNotDeleted);
        dao.persist(demoMerged2);

        DemographicMerged demoMerged3 = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(demoMerged3);
        demoMerged3.setMergedTo(demoId1);
        demoMerged3.setDemographicNo(demoId1);
        demoMerged3.setDeleted(isNotDeleted);
        dao.persist(demoMerged3);

        DemographicMerged demoMerged4 = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(demoMerged4);
        demoMerged4.setMergedTo(demoId1);
        demoMerged4.setDemographicNo(demoId1);
        demoMerged4.setDeleted(isDeleted);
        dao.persist(demoMerged4);

        List<DemographicMerged> expectedResult = new ArrayList<DemographicMerged>(Arrays.asList(demoMerged1, demoMerged3));
        List<DemographicMerged> result = dao.findCurrentByDemographicNo(demoId1);
        Logger logger = MiscUtils.getLogger();
        if (result.size() != expectedResult.size()) {
            logger.warn("Array sizes do not match. Result: " + result.size());
            fail("Array sizes do not match.");
        }

        for (int i = 0; i < expectedResult.size(); i++) {
            if (!expectedResult.get(i).equals(result.get(i))) {
                logger.warn("Items do not match.");
                fail("Items do not match.");
            }
        }
        assertTrue(true);
    }

    @Test
    public void testFindByDemographicNo() throws Exception {
        // Creating new demographics in this test to enforce foreign key addition of "demographic_id" in the "demographic_merged" table
        Demographic demo1 = new Demographic();
        EntityDataGenerator.generateTestDataForModelClass(demo1);
        demo1.setDemographicNo(null);
        demographicDao.save(demo1);

        Demographic demo2 = new Demographic();
        EntityDataGenerator.generateTestDataForModelClass(demo2);
        demo2.setDemographicNo(null);
        demographicDao.save(demo2);

        int demoId1 = demo1.getDemographicNo();
        int demoId2 = demo2.getDemographicNo();

        DemographicMerged demoMerged1 = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(demoMerged1);
        demoMerged1.setMergedTo(demoId1);
        demoMerged1.setDemographicNo(demoId1);
        dao.persist(demoMerged1);

        DemographicMerged demoMerged2 = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(demoMerged2);
        demoMerged2.setMergedTo(demoId2);
        demoMerged2.setDemographicNo(demoId2);
        dao.persist(demoMerged2);

        DemographicMerged demoMerged3 = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(demoMerged3);
        demoMerged3.setMergedTo(demoId2);
        demoMerged3.setDemographicNo(demoId2);
        dao.persist(demoMerged3);

        List<DemographicMerged> expectedResult = new ArrayList<DemographicMerged>(Arrays.asList(demoMerged1));
        List<DemographicMerged> result = dao.findByDemographicNo(demoId1);

        Logger logger = MiscUtils.getLogger();
        if (result.size() != expectedResult.size()) {
            logger.warn("Array sizes do not match.");
            fail("Array sizes do not match.");
        }

        for (int i = 0; i < expectedResult.size(); i++) {
            if (!expectedResult.get(i).equals(result.get(i))) {
                logger.warn("Items do not match.");
                fail("Items do not match.");
            }
        }
        assertTrue(true);
    }
}