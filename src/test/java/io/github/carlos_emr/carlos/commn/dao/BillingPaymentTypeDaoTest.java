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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.Collections;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import java.util.Comparator;

public class BillingPaymentTypeDaoTest extends DaoTestFixtures {

    protected BillingPaymentTypeDao dao = SpringUtils.getBean(BillingPaymentTypeDao.class);

    @Before
    public void before() throws Exception {
        SchemaUtils.restoreTable(false, "billing_payment_type");
    }

    @Test
    public void testCreate() throws Exception {
        BillingPaymentType entity = new BillingPaymentType();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertNotNull(entity.getId());
    }

    @Test
    public void testFindAll() throws Exception {

        BillingPaymentType bPT1 = new BillingPaymentType();
        EntityDataGenerator.generateTestDataForModelClass(bPT1);
        dao.persist(bPT1);

        BillingPaymentType bPT2 = new BillingPaymentType();
        EntityDataGenerator.generateTestDataForModelClass(bPT2);
        dao.persist(bPT2);

        BillingPaymentType bPT3 = new BillingPaymentType();
        EntityDataGenerator.generateTestDataForModelClass(bPT3);
        dao.persist(bPT3);

        List<BillingPaymentType> expectedResult = new ArrayList<BillingPaymentType>(Arrays.asList(bPT1, bPT2, bPT3));
        List<BillingPaymentType> result = dao.findAll();

        result.sort(Comparator.comparingInt(BillingPaymentType::getId));
        Logger logger = MiscUtils.getLogger();

        if (result.size() != expectedResult.size()) {
            logger.warn("Array sizes do not match.");
            fail("Array sizes do not match.");
        }
        for (int i = 0; i < expectedResult.size(); i++) {
            if (!expectedResult.get(i).equals(result.get(i))) {
                logger.warn("Items  do not match.");
                fail("Items  do not match.");
            }
        }
        assertTrue(true);
    }
}