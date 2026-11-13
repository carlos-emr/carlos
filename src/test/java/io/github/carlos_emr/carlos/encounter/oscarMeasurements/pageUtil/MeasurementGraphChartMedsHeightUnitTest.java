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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;
import io.github.carlos_emr.carlos.eform.util.BrowserChartRenderer;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * #6165: the medication-profile graph gives each drug about 30 px, but is never drawn shorter than
 * the default 600 px. FAKE drug identifiers only.
 */
@Tag("unit")
@Tag("measurement")
@DisplayName("Medication-profile graph height")
class MeasurementGraphChartMedsHeightUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 101;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo login;

    @BeforeEach
    void setUp() {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        createAndRegisterMock(MeasurementDao.class);
        createAndRegisterMock(MeasurementsExtDao.class);
        login = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest("GET", "/carlos/encounter/GraphMeasurements");
        request.setContextPath("/carlos");
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", "999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        request.setParameter("demographicNo", String.valueOf(PATIENT));
        request.setParameter("method", "ChartMeds");
        response = new MockHttpServletResponse();
        when(security.hasPrivilege(login, "_measurement", "r", String.valueOf(PATIENT))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, PATIENT)).thenReturn(true);
    }

    @ParameterizedTest(name = "{0} drugs: {1} px")
    @CsvSource({"0, 600", "10, 600", "11, 600", "15, 600", "19, 600", "20, 600", "21, 630", "25, 750"})
    @DisplayName("should keep the default height until the drug rows need more room")
    void shouldKeepTheDefaultHeight_untilTheDrugRowsNeedMoreRoom(int drugs, int expectedHeight) throws Exception {
        if (drugs > 0) {
            request.setParameter("drug", IntStream.range(0, drugs).mapToObj(index -> "FAKE-DIN-" + index).toArray(String[]::new));
        }

        assertThat(renderedHeight()).isEqualTo(expectedHeight);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
    }

    /** Draws the graph with no prescriptions on file and returns the height it was drawn at. */
    private int renderedHeight() throws Exception {
        DemographicNameAgeString nameLookup = mock(DemographicNameAgeString.class);
        when(nameLookup.getNameAgeString(login, PATIENT)).thenReturn("FAKE patient");
        AtomicInteger height = new AtomicInteger();
        try (var servlet = mockStatic(ServletActionContext.class);
             var names = mockStatic(DemographicNameAgeString.class);
             var images = mockStatic(BrowserChartRenderer.class);
             var _ = mockConstruction(RxPrescriptionData.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            names.when(DemographicNameAgeString::getInstance).thenReturn(nameLookup);
            images.when(() -> BrowserChartRenderer.write(any(), any(), any(), anyInt(), anyInt(), anyString()))
                    .thenAnswer(invocation -> {
                        height.set(invocation.getArgument(4));
                        return null;
                    });

            assertThat(new MeasurementGraphAction22Action().execute()).isEqualTo("none");
        }
        return height.get();
    }
}
