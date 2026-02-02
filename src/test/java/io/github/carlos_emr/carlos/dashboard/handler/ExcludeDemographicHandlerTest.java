/**
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
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
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 */

package io.github.carlos_emr.carlos.dashboard.handler;

import org.junit.BeforeClass;
import org.junit.Test;

import io.github.carlos_emr.carlos.commn.dao.DaoTestFixtures;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import static org.junit.Assert.*;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;

public class ExcludeDemographicHandlerTest extends DaoTestFixtures {

    private static DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    private static ExcludeDemographicHandler excludeDemographicHandler;
    private static String indicatorName;
    Date now = new java.util.Date();
    static Demographic demographic;
    static String providerNo = "100";
    static List<Integer> demoNos = new ArrayList<Integer>();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {       
        SchemaUtils.restoreTable("demographic", "lst_gender", "admission", "demographic_merged",
                "program", "health_safety", "provider", "providersite", "site", "program_team",
                "log", "Facility", "demographicExt");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoAsCurrentClassAndMethod();
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        for (int i = 0; i < 10; i++) {
            demographic = new Demographic();
            EntityDataGenerator.generateTestDataForModelClass(demographic);
            demographic.setDemographicNo(null);
            demographic.setProvider(provider);
            demographicDao.save(demographic);
            demoNos.add(demographic.getDemographicNo());
        }
        loggedInInfo.setLoggedInProvider(provider);
        excludeDemographicHandler = new ExcludeDemographicHandler();
        excludeDemographicHandler.setLoggedinInfo(loggedInInfo);
    }

    @Test
    public void getDemoIds() {
        indicatorName = "indicatorName_getDemoIds";
        assertEquals(0, excludeDemographicHandler.getDemoIds(indicatorName).size());
    }

    @Test
    public void getDemoExts() {
        indicatorName = "indicatorName_getDemoExts";
        assertEquals(0, excludeDemographicHandler.getDemoExts(indicatorName).size());
    }

    @Test
    public void setDemoId() {
        indicatorName = "myIndicatorName_setDemoId";
        assertEquals(10, demoNos.size());
        excludeDemographicHandler.excludeDemoId(demoNos.get(5), indicatorName);
        assertEquals(1, excludeDemographicHandler.getDemoIds(indicatorName).size());
        assertTrue(excludeDemographicHandler.getDemoIds(indicatorName).get(0).equals(demoNos.get(5)));
        //Have to remove previous demographicExt before moving to new tests due to uk_demo_ext.
        excludeDemographicHandler.unExcludeDemoIds(demoNos, indicatorName);
    }

    @Test
    public void setDemoIDList() {
        indicatorName = "myIndicatorName_setDemoIDList";
        excludeDemographicHandler.excludeDemoIds(demoNos, indicatorName);
        List<Integer> demoIds = excludeDemographicHandler.getDemoIds(indicatorName);
        assertEquals(demoNos.size(), demoIds.size());
        for (Integer el : demoNos) {
            assertTrue(demoIds.contains(el));
        }
        //Have to remove previous demographicExt before moving to new tests due to uk_demo_ext.
        excludeDemographicHandler.unExcludeDemoIds(demoNos, indicatorName);
    }

    @Test
    public void unsetDemoIDList() {
        indicatorName = "myIndicatorName_unsetDemoIDList";
        excludeDemographicHandler.excludeDemoIds(demoNos, indicatorName);
        List<Integer> demoIds = excludeDemographicHandler.getDemoIds(indicatorName);
        assertEquals(demoNos.size(), demoIds.size());
        excludeDemographicHandler.unExcludeDemoIds(demoNos, indicatorName);
        assertEquals(0, excludeDemographicHandler.getDemoIds(indicatorName).size());
    }

    @Test
    public void setDemoIdJson() {
        indicatorName = "myIndicatorName_setDemoIdJson";
        String jsonStr = getJsonDemoNoStr(demoNos);
        excludeDemographicHandler.excludeDemoIds(jsonStr, indicatorName);
        List<Integer> demoIds = excludeDemographicHandler.getDemoIds(indicatorName);
        for (int demoNo : demoNos) {
            assertTrue(demoIds.contains(demoNo));
        }
        //Have to remove previous demographicExt before moving to new tests due to uk_demo_ext.
        excludeDemographicHandler.unExcludeDemoIds(jsonStr, indicatorName);
    }

    @Test
    public void unsetDemoIdJson() {
        indicatorName = "myIndicatorName_unsetDemoIdJson";
        String jsonStr = getJsonDemoNoStr(demoNos);
        excludeDemographicHandler.excludeDemoIds(jsonStr, indicatorName);
        List<Integer> demoIds = excludeDemographicHandler.getDemoIds(indicatorName);
        for (int demoNo : demoNos) {
            assertTrue(demoIds.contains(demoNo));
        }
        excludeDemographicHandler.unExcludeDemoIds(jsonStr, indicatorName);
        assertEquals(0, excludeDemographicHandler.getDemoIds(indicatorName).size());
    }

    private static String getJsonDemoNoStr(List<Integer> demoNums) {
        StringBuilder builder = new StringBuilder();
        for (Integer i : demoNums) {
            builder.append(i + ",");
        }
        builder.deleteCharAt(builder.length() - 1);  //remove trailing comma
        String str = builder.toString();
        return str;
    }
}