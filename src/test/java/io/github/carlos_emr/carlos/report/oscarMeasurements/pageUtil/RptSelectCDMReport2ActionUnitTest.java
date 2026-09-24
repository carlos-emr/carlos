/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The CDM entry page posts the chosen group as {@code value(CDMgroup)}. Struts 7 never binds that
 * name to {@code setValue}, so before the fix the action saw a {@code null} group and every CDM
 * screen rendered no rows. These tests drive the action with only the raw request parameter, as
 * Tomcat delivers it.
 */
@Tag("unit")
@Tag("report")
@Tag("measurement")
@DisplayName("RptSelectCDMReport2Action")
class RptSelectCDMReport2ActionUnitTest extends CarlosUnitTestBase {

    private static final String GROUP = "FAKE-OMD-CDM";
    private static final String AACP_NAME = "Asthma Action Plan ";

    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfo;
    private HttpServletRequest request;
    private HttpSession session;
    private MeasurementGroupDao groupDao;

    @BeforeEach
    void setUp() {
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        groupDao = createAndRegisterMock(MeasurementGroupDao.class);
        MeasurementTypeDao typeDao = createAndRegisterMock(MeasurementTypeDao.class);
        MeasurementDao measurementDao = createAndRegisterMock(MeasurementDao.class);

        request = mock(HttpServletRequest.class);
        session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(mock(HttpServletResponse.class));
        loggedInInfo = mockStatic(LoggedInInfo.class);
        loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mock(LoggedInInfo.class));
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_report"), eq("r"), isNull()))
                .thenReturn(true);

        MeasurementGroup groupRow = new MeasurementGroup();
        groupRow.setName(GROUP);
        groupRow.setTypeDisplayName(AACP_NAME);
        when(groupDao.findByName(GROUP)).thenReturn(new ArrayList<>(List.of(groupRow)));

        MeasurementType aacp = mock(MeasurementType.class);
        when(aacp.getId()).thenReturn(6);
        when(aacp.getType()).thenReturn("AACP");
        when(aacp.getTypeDisplayName()).thenReturn(AACP_NAME);
        when(aacp.getTypeDescription()).thenReturn(AACP_NAME);
        when(aacp.getMeasuringInstruction()).thenReturn("Provided/Revised/Reviewed");
        when(aacp.getValidation()).thenReturn("18");
        when(typeDao.findByTypeDisplayName(AACP_NAME)).thenReturn(List.of(aacp));
        when(measurementDao.findDistinctMeasuringInstructionsByTypes(anyCollection()))
                .thenReturn(Map.of("AACP", List.of("Yes/No", "Provided/Revised/Reviewed")));
    }

    @AfterEach
    void tearDown() {
        loggedInInfo.close();
        servletActionContext.close();
    }

    @Test
    @DisplayName("should build the report rows for the group posted as value(CDMgroup)")
    void shouldBuildRows_fromPostedMappedGroupParameter() throws Exception {
        when(request.getParameter("value(CDMgroup)")).thenReturn(GROUP);
        RptSelectCDMReport2Action action = new RptSelectCDMReport2Action();
        action.setForward("patientWhoMetGuideline");

        String result = action.execute();

        assertThat(result).isEqualTo("patientWhoMetGuideline");
        verify(groupDao).findByName(GROUP);
        verify(session).setAttribute("CDMGroup", GROUP);
        ArgumentCaptor<Object> handler = ArgumentCaptor.forClass(Object.class);
        verify(session).setAttribute(eq("measurementTypes"), handler.capture());
        List<RptMeasurementTypesBean> rows =
                ((RptMeasurementTypesBeanHandler) handler.getValue()).getMeasurementTypeVector();
        assertThat(rows).extracting(RptMeasurementTypesBean::getType).containsExactly("AACP");
    }

    @Test
    @DisplayName("should prefer a value set through setValue over the request parameter")
    void shouldPreferSetValue_overRequestParameter() {
        when(request.getParameter("value(CDMgroup)")).thenReturn("from-request");
        RptSelectCDMReport2Action action = new RptSelectCDMReport2Action();
        action.setValue("CDMgroup", "from-setter");

        assertThat(action.getValue("CDMgroup")).isEqualTo("from-setter");
    }
}
