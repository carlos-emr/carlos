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
package io.github.carlos_emr.carlos.prescript.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Patient-scoped authorisation on every Rx view gate whose JSP picks its patient from the
 * request (per-patient Rx state, #3875): global Rx/allergy read is not enough to render a patient
 * the URL names.
 *
 * @since 2026-09-24
 */
@DisplayName("Rx view gates authorise the requested patient")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxRequestedPatientAccessUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 42;

    private MockHttpServletRequest request;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    /** Every gate whose JSP resolves its Rx patient per request: {class, security object, privilege}. */
    static Stream<Arguments> patientResolvingGates() {
        return Stream.of(
                Arguments.of(ViewDisplayMedHistory2Action.class, "_rx", "r"),
                Arguments.of(ViewInteractionDisplay2Action.class, "_rx", "r"),
                Arguments.of(ViewListDrugs2Action.class, "_rx", "r"),
                Arguments.of(ViewManagePharmacy22Action.class, "_rx", "w"),
                Arguments.of(ViewPreview22Action.class, "_rx", "r"),
                Arguments.of(ViewPrint2Action.class, "_rx", "r"),
                Arguments.of(ViewPrintDrugProfile22Action.class, "_rx", "r"),
                Arguments.of(ViewShowPreviousPrints2Action.class, "_rx", "r"),
                Arguments.of(ViewSideLinksEditFavorites22Action.class, "_allergy", "r"),
                Arguments.of(ViewSideLinksNoEditFavorites22Action.class, "_allergy", "r"),
                Arguments.of(ViewSideLinksNoEditFavorites2Action.class, "_allergy", "r"),
                Arguments.of(ViewStaticScript2Action.class, "_rx", "r"),
                Arguments.of(ViewUpdateInteractingDrugs2Action.class, "_rx", "r"),
                Arguments.of(ViewViewPharmacy2Action.class, "_rx", "r"));
    }

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/rx/view");
        request.addParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), anyString(), anyString(), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), anyString(), anyString(), eq(DEMOGRAPHIC_NO)))
                .thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("capturing the fallback patient does not cache or bypass patient authorization")
    void shouldReauthorizeCapturedPatient_whenActivePatientChanges() {
        request.removeParameter("demographicNo");
        RxSessionBean original = new RxSessionBean();
        original.setDemographicNo(DEMOGRAPHIC_NO);
        RxSessionBeanResolver.register(request.getSession(), original);
        RxRequestedPatientAccess.require(securityInfoManager, loggedInInfo, request, "_rx", "r");
        RxSessionBean another = new RxSessionBean();
        another.setDemographicNo(99);
        RxSessionBeanResolver.register(request.getSession(), another);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", 99)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 99)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", DEMOGRAPHIC_NO)).thenReturn(false);

        assertThatThrownBy(() -> RxRequestedPatientAccess.require(
                securityInfoManager, loggedInInfo, request, "_rx", "r"))
                .isInstanceOf(SecurityException.class);
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_rx", "r", 99);
    }

    private static String run(Class<? extends ActionSupport> gate) throws Exception {
        return gate.getDeclaredConstructor().newInstance().execute();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("patientResolvingGates")
    @DisplayName("should forward when the caller may access the named patient")
    void shouldForward_whenNamedPatientIsAuthorised(Class<? extends ActionSupport> gate, String object,
                                                   String privilege) throws Exception {
        assertThat(run(gate)).isEqualTo(ActionSupport.SUCCESS);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, object, privilege, DEMOGRAPHIC_NO);
        verify(securityInfoManager).isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("patientResolvingGates")
    @DisplayName("should refuse a patient the caller may not access at patient level, despite the global privilege")
    void shouldThrow_whenPatientLevelPrivilegeIsDenied(Class<? extends ActionSupport> gate, String object,
                                                      String privilege) {
        when(securityInfoManager.hasPrivilege(loggedInInfo, object, privilege, DEMOGRAPHIC_NO)).thenReturn(false);

        assertThatThrownBy(() -> run(gate))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (" + object + ")");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("patientResolvingGates")
    @DisplayName("should refuse a patient whose record the caller may not open")
    void shouldThrow_whenPatientRecordAccessIsDenied(Class<? extends ActionSupport> gate, String object,
                                                    String privilege) {
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(false);

        assertThatThrownBy(() -> run(gate))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (" + object + ")");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("patientResolvingGates")
    @DisplayName("should read the legacy demographic_no parameter as the named patient too")
    void shouldThrow_whenLegacyParameterNamesDeniedPatient(Class<? extends ActionSupport> gate, String object,
                                                          String privilege) {
        request.removeParameter("demographicNo");
        request.addParameter("demographic_no", String.valueOf(DEMOGRAPHIC_NO));
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(false);

        assertThatThrownBy(() -> run(gate)).isInstanceOf(SecurityException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("patientResolvingGates")
    @DisplayName("should leave a request that names no patient to the page")
    void shouldSkipPatientCheck_whenNoPatientNamed(Class<? extends ActionSupport> gate, String object,
                                                   String privilege) throws Exception {
        request.removeParameter("demographicNo");

        assertThat(run(gate)).isEqualTo(ActionSupport.SUCCESS);
        verify(securityInfoManager, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("patientResolvingGates")
    @DisplayName("should refuse a malformed or conflicting patient instead of treating it as none")
    void shouldThrow_whenRequestedPatientIsInvalid(Class<? extends ActionSupport> gate, String object,
                                                  String privilege) {
        request.removeParameter("demographicNo");
        request.addParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.addParameter("demographic_no", String.valueOf(DEMOGRAPHIC_NO + 1));

        assertThatThrownBy(() -> run(gate))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (" + object + ")");
        verify(securityInfoManager, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @Test
    @DisplayName("should refuse a non-numeric patient number")
    void shouldThrow_whenRequestedPatientIsMalformed() {
        request.removeParameter("demographicNo");
        request.addParameter("demographicNo", "42abc");

        assertThatThrownBy(() -> run(ViewPrintDrugProfile22Action.class))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
    }

    @Test
    @DisplayName("should refuse before any patient check when the global privilege is missing")
    void shouldThrow_whenGlobalPrivilegeIsDenied() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"), eq("r"), isNull())).thenReturn(false);

        assertThatThrownBy(() -> run(ViewPrintDrugProfile22Action.class))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
        verify(securityInfoManager, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }
}
