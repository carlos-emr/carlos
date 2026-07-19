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
package io.github.carlos_emr.carlos.billing.CA.dao;

import static org.junit.Assert.assertNotNull;

import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import org.junit.Before;
import org.junit.Test;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.commn.dao.DaoTestFixtures;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class BillingDetailDaoTest extends DaoTestFixtures {

    public BillingDetailDao dao = SpringUtils.getBean(BillingDetailDao.class);

    public BillingDetailDaoTest() {
    }

    @Before
    public void before() throws Exception {
        SchemaUtils.restoreTable("billingdetail");
    }

    @Test
    public void testCreate() throws Exception {
        BillingDetail entity = new BillingDetail();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertNotNull(entity.getId());
    }

    @Test
    public void testFindByBillingNoAndStatus() {
        assertNotNull(dao.findByBillingNoAndStatus(100, "STS"));
    }
}
