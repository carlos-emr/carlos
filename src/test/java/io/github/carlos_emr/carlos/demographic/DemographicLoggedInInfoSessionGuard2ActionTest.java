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
package io.github.carlos_emr.carlos.demographic;

import java.util.stream.Stream;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Demographic loggedInInfo session guard 2Action tests")
@Tag("integration")
@Tag("web")
@Tag("demographic")
class DemographicLoggedInInfoSessionGuard2ActionTest extends CarlosWebTestBase {

    private static final String LOGGED_IN_INFO_SESSION_KEY = new LoggedInInfo().getLoggedInInfoKey();

    @BeforeEach
    void setUp() {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        setSessionAttribute("user", "999998");
        setSessionAttribute(LOGGED_IN_INFO_SESSION_KEY, mockLoggedInInfo);
    }

    @DisplayName("should throw SecurityException when loggedInInfo session attribute is missing")
    @ParameterizedTest(name = "{0}")
    @MethodSource("affectedActionClasses")
    void shouldThrowSecurityException_whenLoggedInInfoSessionAttributeIsMissing(String actionClassName) throws Exception {
        setSessionAttribute(LOGGED_IN_INFO_SESSION_KEY, null);

        ActionSupport action = instantiateAction(actionClassName);

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required session");

        verifyNoInteractions(mockSecurityInfoManager);
    }

    private static Stream<String> affectedActionClasses() {
        return Stream.of(
                "io.github.carlos_emr.carlos.demographic.PrintClientLabLabel2Action",
                "io.github.carlos_emr.carlos.demographic.PrintDemoAddressLabel2Action",
                "io.github.carlos_emr.carlos.demographic.PrintDemoChartLabel2Action",
                "io.github.carlos_emr.carlos.demographic.PrintDemoLabel2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewAddDemoToPatientSet2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewAddNewDemographicSwipe2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewContact2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewContactSearch2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewDemographicAddARecordHtm2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewDemographicAudit2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewDemographicCohort2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewDemographicEditDemographicJs2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewDemographicLabelPrintSetting2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewDemographicPrintDemographic2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewDemographicSearch2ReportResults2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewDisplayFirstNationsModule2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewDisplayHealthCareTeam2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewEnrollmentHistory2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewManageFirstNationsModule2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewManageHealthCareTeam2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewPrintAddressLabel2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewPrintClientLabLabel2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewPrintDemoChartLabel2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewPrintDemoLabel2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewPrintEnvelope2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewProContact2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewProContactSearch2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewProfessionalSpecialistSearch2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewSearch2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewZdemographicFullTitleSearch2Action",
                "io.github.carlos_emr.carlos.demographic.gate.ViewZdemographicSwipe2Action",
                "io.github.carlos_emr.carlos.demographic.pageUtil.AddDemographicRelationship2Action",
                "io.github.carlos_emr.carlos.demographic.pageUtil.DeleteDemographicRelationship2Action",
                "io.github.carlos_emr.carlos.demographic.pageUtil.DemographicExportAction42Action",
                "io.github.carlos_emr.carlos.demographic.pageUtil.DemographicMergeRecord2Action",
                "io.github.carlos_emr.carlos.demographic.pageUtil.ImportLogDownload2Action",
                "io.github.carlos_emr.carlos.demographic.pageUtil.RourkeExport2Action"
        );
    }

    private ActionSupport instantiateAction(String actionClassName) throws Exception {
        return (ActionSupport) Class.forName(actionClassName).getDeclaredConstructor().newInstance();
    }
}
