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
 *
 * <p>
 * Migrated from legacy JUnit 4 test to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.dashboard.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.model.Dashboard;
import io.github.carlos_emr.carlos.commn.model.IndicatorTemplate;
import io.github.carlos_emr.carlos.dashboard.display.beans.DashboardBean;
import io.github.carlos_emr.carlos.dashboard.factory.DashboardBeanFactory;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Unit tests for {@link DashboardBeanFactory}, {@link Dashboard} entity,
 * and the construction of {@link DashboardBean}.
 *
 * <p>Tests that a DashboardBeanFactory correctly copies Dashboard entity
 * properties into a DashboardBean for display.
 *
 * <p>Migrated from legacy JUnit 4 DashboardBeanFactoryTest. The legacy test
 * had all @Test annotations commented out due to a missing constructor call.
 * This modern version properly instantiates the factory.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("dashboard")
@DisplayName("DashboardBeanFactory unit tests")
class DashboardBeanFactoryUnitTest {

    private static DashboardBeanFactory dashboardBeanFactory;
    private static Dashboard dashboard;
    private static DashboardBean dashboardBean;
    private static MockedStatic<SpringUtils> springUtilsMock;

    @BeforeAll
    static void setUpBeforeAll() throws IOException {
        springUtilsMock = Mockito.mockStatic(SpringUtils.class);
        springUtilsMock.when(() -> SpringUtils.getBean(DashboardManager.class))
                .thenReturn(Mockito.mock(DashboardManager.class));
        springUtilsMock.when(() -> SpringUtils.getBean(DemographicExtDao.class))
                .thenReturn(Mockito.mock(DemographicExtDao.class));
        URL url = Thread.currentThread().getContextClassLoader()
                .getResource("indicatorXMLTemplates/diabetes_hba1c_test.xml");
        byte[] byteArray;
        try (InputStream is = url.openStream()) {
            byteArray = IOUtils.toByteArray(is);
        }

        // Create the handler and extract an IndicatorTemplate entity
        IndicatorTemplateHandler indicatorTemplateHandler = new IndicatorTemplateHandler(byteArray);
        IndicatorTemplate indicatorTemplate = indicatorTemplateHandler.getIndicatorTemplateEntity();

        // Build a list of IndicatorTemplates
        ArrayList<IndicatorTemplate> indicatorTemplateList = new ArrayList<>();
        indicatorTemplateList.add(indicatorTemplate);
        indicatorTemplateList.add(indicatorTemplate);

        dashboard = new Dashboard();
        dashboard.setActive(true);
        dashboard.setCreator("colcamex");
        dashboard.setDescription("colcamex test case");
        dashboard.setEdited(new Date(System.currentTimeMillis()));
        dashboard.setId(100);
        dashboard.setLocked(false);
        dashboard.setName("test dashboard");

        // DashboardBeanFactory requires LoggedInInfo
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoAsCurrentClassAndMethod();
        dashboardBeanFactory = new DashboardBeanFactory(loggedInInfo, dashboard, indicatorTemplateList);
        dashboardBean = dashboardBeanFactory.getDashboardBean();
    }

    @AfterAll
    static void tearDownAfterAll() {
        dashboardBeanFactory = null;
        dashboard = null;
        dashboardBean = null;
        if (springUtilsMock != null) {
            springUtilsMock.close();
        }
    }

    @Test
    @DisplayName("should return dashboard bean with matching ID")
    void shouldReturnDashboardBean_withMatchingId() {
        assertThat(dashboardBean.getId() + "").isEqualTo("100");
    }

    @Test
    @DisplayName("should return active status as true")
    void shouldReturnActiveTrue_whenDashboardIsActive() {
        assertThat(dashboardBean.isActive()).isTrue();
    }

    @Test
    @DisplayName("should return creator matching dashboard entity")
    void shouldReturnCreator_matchingDashboardEntity() {
        assertThat(dashboardBean.getCreator()).isEqualTo(dashboard.getCreator());
    }

    @Test
    @DisplayName("should return description matching dashboard entity")
    void shouldReturnDescription_matchingDashboardEntity() {
        assertThat(dashboardBean.getDescription()).isEqualTo(dashboard.getDescription());
    }

    @Test
    @DisplayName("should return edited date matching dashboard entity")
    void shouldReturnEditedDate_matchingDashboardEntity() {
        assertThat(dashboardBean.getEdited()).isEqualTo(dashboard.getEdited());
    }

    @Test
    @DisplayName("should return ID matching dashboard entity")
    void shouldReturnId_matchingDashboardEntity() {
        assertThat(dashboardBean.getId()).isEqualTo(dashboard.getId());
    }

    @Test
    @DisplayName("should return locked status as false")
    void shouldReturnLockedFalse_whenDashboardIsNotLocked() {
        assertThat(dashboardBean.isLocked()).isFalse();
    }

    @Test
    @DisplayName("should return name matching dashboard entity")
    void shouldReturnName_matchingDashboardEntity() {
        assertThat(dashboardBean.getName()).isEqualTo(dashboard.getName());
    }
}
