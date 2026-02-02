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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingTrayFee;
import io.github.carlos_emr.carlos.commn.dao.DaoTestFixtures;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.billings.ca.bc.data.SupServiceCodeAssocDAO;

public class SupServiceCodeAssocDAOTest extends DaoTestFixtures {

    public SupServiceCodeAssocDAO dao = SpringUtils.getBean(SupServiceCodeAssocDAO.class);

    @Before
    public void before() throws Exception {
        SchemaUtils.restoreTable("billing_trayfees", "billingservice");
    }

    @Test
    public void testAllAtOnce() throws Exception {
        BillingTrayFee fee = new BillingTrayFee();
        EntityDataGenerator.generateTestDataForModelClass(fee);
        fee.setBillingServiceNo("999999");
        fee.setBillingServiceTrayNo("0000000");
        fee.setId(null);

        dao.persist(fee);

        List<?> list = dao.getServiceCodeAssociactions();
        assertFalse(list.isEmpty());

        Map<?, ?> map = dao.getAssociationKeyValues();
        assertFalse(map.isEmpty());

        dao.saveOrUpdateServiceCodeAssociation(fee.getId().toString(), "888888");
        dao.saveOrUpdateServiceCodeAssociation("77777777", "888888");

        dao.deleteServiceCodeAssociation(fee.getId().toString());

        fee = dao.find(fee.getId());
        assertTrue(fee == null);
    }
}
