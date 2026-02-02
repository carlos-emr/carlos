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

import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.commn.model.WaitingListName;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

public class WaitingListDaoTest extends DaoTestFixtures {

    protected WaitingListDao dao = SpringUtils.getBean(WaitingListDao.class);

    @Before
    public void before() throws Exception {
        beforeForInnoDB();
        SchemaUtils.restoreTable("Facility", "lst_gender", "demographic_merged", "admission", "health_safety", "program", "waitingList", "waitingListName", "demographic", "appointment");
    }

    @Test
    public void testCreate() throws Exception {
        WaitingList entity = new WaitingList();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertNotNull(entity.getId());
    }

    @Test
    public void testFindByDemographic() {
        // WaitingListNameDao wlnDao = SpringUtils.getBean(WaitingListNameDao.class);
        WaitingListName wn = new WaitingListName();
        wn.setCreateDate(new Date());
        wn.setName("NAHBLIAYH");
        wn.setGroupNo("1");
        wn.setIsHistory("N");
        wn.setProviderNo("1");
        dao.persist(wn);

        WaitingList w = new WaitingList();
        w.setDemographicNo(10);
        w.setListId(wn.getId());
        w.setOnListSince(new Date());
        w.setPosition(1);
        w.setIsHistory("N");
        dao.persist(w);

        List<Object[]> lists = dao.findByDemographic(ConversionUtils.fromIntString("10"));
        assertNotNull(lists);
        assertTrue(lists.size() == 1);
    }

    @Test
    public void testFindWaitingListsAndDemographics() {
        List<Object[]> results = dao.findWaitingListsAndDemographics(1);
        assertNotNull(results);
    }

    @Test
    public void testFindAppts() {
        WaitingList w = new WaitingList();
        w.setDemographicNo(1);
        w.setOnListSince(new Date());
        List<Appointment> appts = dao.findAppointmentFor(w);
        assertNotNull(appts);
    }

    @Test
    public void testFBWLIADI() {
        List<WaitingList> wls = dao.findByWaitingListIdAndDemographicId(1, 1);
        assertNotNull(wls);
    }

    @Test
    public void testMaxPosition() {
        Integer i = dao.getMaxPosition(1);
        assertNotNull(i);
    }

}
