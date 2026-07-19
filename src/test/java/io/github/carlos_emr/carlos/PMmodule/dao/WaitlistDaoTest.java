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
package io.github.carlos_emr.carlos.PMmodule.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import io.github.carlos_emr.carlos.PMmodule.dao.WaitlistDao;
import org.junit.Before;
import org.junit.Test;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.CriteriaBO;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.CriteriasBO;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.VacancyDisplayBO;
import io.github.carlos_emr.carlos.commn.dao.DaoTestFixtures;
import io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class WaitlistDaoTest extends DaoTestFixtures {

    public WaitlistDao dao = SpringUtils.getBean(WaitlistDao.class);

    @Before
    public void before() throws Exception {
        SchemaUtils.restoreTable("criteria", "demographic", "vacancy_client_match", "eform_data", "eform_values", "vacancy_client_match", "demographic", "vacancy_template", "vacancy", "client_referral", "program", "criteria_selection_option");
    }

    @Test
    public void testGetClientMatches() {
        assertNotNull(dao.getClientMatches(1));
    }

    @Test
    public void testSearchForMatchingEforms() {
        CriteriasBO crits = new CriteriasBO();
        CriteriaBO crit = new CriteriaBO();
        crit.value = "test";
        CriteriaBO[] critArray = {crit};
        crits.crits = critArray;

        assertEquals(0, dao.searchForMatchingEforms(crits).size());

    }

    @Test
    public void testListDisplayVacanciesForWaitListProgram() {
        assertNotNull(dao.listDisplayVacanciesForWaitListProgram(1));
    }

    @Test
    public void testListDisplayVacanciesForAllWaitListPrograms() {
        assertNotNull(dao.listDisplayVacanciesForAllWaitListPrograms());
    }

    @Test
    public void testGetDisplayVacanciesForAgencyProgram() {
        assertNotNull(dao.getDisplayVacanciesForAgencyProgram(1));
    }

    @Test
    public void testGetDisplayVacancy() {
        assertNull(dao.getDisplayVacancy(45));
    }

    @Test
    public void testLoadStats() {
        VacancyDisplayBO vd = new VacancyDisplayBO();
        vd.setVacancyID(1);
        dao.loadStats(vd);
    }

}
