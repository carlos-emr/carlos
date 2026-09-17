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

import java.io.InputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import jakarta.servlet.http.HttpServletRequest;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises authorization and nullable preferences through each public PDF action. */
@Tag("unit")
class DemographicLabelActionUnitTest extends CarlosUnitTestBase {
    enum Route {
        DEMOGRAPHIC(PrintDemoLabel2Action::new),
        ADDRESS(PrintDemoAddressLabel2Action::new),
        CHART(PrintDemoChartLabel2Action::new),
        CLIENT_LAB(PrintClientLabLabel2Action::new);

        private final Supplier<ActionSupport> action;
        Route(Supplier<ActionSupport> action) { this.action = action; }
    }

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private UserPropertyDAO preferences;
    private ProgramManager2 programs;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> session;
    private MockedStatic<DemographicLabelPdf> pdf;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setParameter("demographic_no", "12345");
        security = mock(SecurityInfoManager.class);
        preferences = mock(UserPropertyDAO.class);
        programs = mock(ProgramManager2.class);
        loggedInInfo = mock(LoggedInInfo.class);
        Provider provider = mock(Provider.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(UserPropertyDAO.class, preferences);
        registerMock(ProgramManager2.class, programs);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(loggedInInfo.getLoggedInProvider()).thenReturn(provider);
        when(provider.getProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(loggedInInfo), eq("_demographic"), eq("r"), any()))
                .thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        session = mockStatic(LoggedInInfo.class);
        session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        pdf = mockStatic(DemographicLabelPdf.class);
    }

    @AfterEach
    void tearDown() {
        if (pdf != null) pdf.close();
        if (session != null) session.close();
        if (servlet != null) servlet.close();
    }

    @ParameterizedTest @EnumSource(Route.class)
    void shouldRejectPatientDenial_beforeLoadingLabel(Route route) {
        when(security.hasPrivilege(loggedInInfo, "_demographic", "r", "12345"))
                .thenReturn(false);
        ActionSupport action = route.action.get();
        assertThrows(SecurityException.class, action::execute);
        assertNoLabelAccess();
    }

    @ParameterizedTest @EnumSource(Route.class)
    void shouldRejectCanonicalPatientDenial_whenIdHasLeadingZeros(Route route) {
        request.setParameter("demographic_no", "0012345");
        when(security.hasPrivilege(loggedInInfo, "_demographic", "r", "12345"))
                .thenReturn(false);
        ActionSupport action = route.action.get();
        assertThrows(SecurityException.class, action::execute);
        assertNoLabelAccess();
    }

    @ParameterizedTest @EnumSource(Route.class)
    void shouldRespectRawPatientDenial_beforeNormalizingId(Route route) {
        request.setParameter("demographic_no", "0012345");
        when(security.hasPrivilege(loggedInInfo, "_demographic", "r", "0012345"))
                .thenReturn(false);
        ActionSupport action = route.action.get();
        assertThrows(SecurityException.class, action::execute);
        verify(security, never()).hasPrivilege(loggedInInfo, "_demographic", "r", "12345");
        assertNoLabelAccess();
    }

    static Stream<Arguments> invalidIds() {
        return Arrays.stream(Route.values()).flatMap(route ->
                Stream.of(null, "", "abc", "0", "-1", "2147483648", "1 OR 1=1")
                        .map(id -> Arguments.of(route, id)));
    }

    @ParameterizedTest @MethodSource("invalidIds")
    void shouldReturnBadRequest_whenPatientIdInvalid(Route route, String id) throws Exception {
        if (id == null) request.removeParameter("demographic_no");
        else request.setParameter("demographic_no", id);
        assertThat(route.action.get().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        assertNoLabelAccess();
    }

    @ParameterizedTest @EnumSource(Route.class)
    void shouldUseCanonicalAuthorizedPatient_whenIdHasLeadingZeros(Route route) throws Exception {
        request.setParameter("demographic_no", "0012345");
        assertRendered(route, null);
        verify(security).hasPrivilege(loggedInInfo, "_demographic", "r", "0012345");
        verify(security).hasPrivilege(loggedInInfo, "_demographic", "r", "12345");
    }

    @ParameterizedTest @EnumSource(Route.class)
    void shouldRenderWithoutAutomaticPrinting_whenStoredPreferencesNull(Route route) throws Exception {
        when(preferences.getProp(eq("999998"), anyString())).thenReturn(new UserProperty());
        assertRendered(route, null);
    }

    @ParameterizedTest @EnumSource(Route.class)
    void shouldKeepPrintDialog_whenSilentPreferenceNull(Route route) throws Exception {
        stubPreferences(null);
        assertRendered(route, false);
    }

    @ParameterizedTest @EnumSource(Route.class)
    void shouldEnableSilentPrinting_whenPreferenceYes(Route route) throws Exception {
        stubPreferences("yes");
        assertRendered(route, true);
    }

    @ParameterizedTest @EnumSource(Route.class)
    void shouldUseBundledTemplate_whenOverridePathRejected(Route route) throws Exception {
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class)) {
            paths.when(() -> PathValidationUtils.resolveTrustedPath(any(File.class)))
                    .thenThrow(new SecurityException("Invalid override path"));
            assertRendered(route, null);
        }
    }

    @ParameterizedTest @EnumSource(Route.class)
    void shouldUseBundledTemplate_whenOverrideCannotBeOpened(Route route, @TempDir Path directory)
            throws Exception {
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class)) {
            // An existing directory passes File.exists(), but cannot be opened as a template.
            paths.when(() -> PathValidationUtils.resolveTrustedPath(any(File.class)))
                    .thenReturn(directory.toFile());
            assertRendered(route, null);
        }
    }

    private void stubPreferences(String silentValue) {
        // Each action reads its printer first and its silent-print flag second.
        UserProperty printer = new UserProperty();
        printer.setValue("Test printer");
        UserProperty silent = new UserProperty();
        silent.setValue(silentValue);
        when(preferences.getProp(eq("999998"), anyString())).thenReturn(printer, silent);
    }

    private void assertRendered(Route route, Boolean silent) throws Exception {
        pdf.when(() -> DemographicLabelPdf.write(eq(response), anyMap(), any(), any()))
                .thenAnswer(invocation -> {
                    Map<String, Object> parameters = invocation.getArgument(1);
                    assertThat(parameters).containsEntry("demo", "12345");
                    try (InputStream template = invocation.getArgument(2)) {
                        assertThat(template).isNotNull();
                    }
                    String javascript = invocation.getArgument(3);
                    if (silent == null) assertThat(javascript).isNull();
                    else {
                        assertThat(javascript).contains("this.print(params)", "Test printer");
                        if (silent) assertThat(javascript).contains("interactionLevel.silent");
                        else assertThat(javascript).doesNotContain("interactionLevel.silent");
                    }
                    return null;
                });
        assertThat(route.action.get().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(200);
        pdf.verify(() -> DemographicLabelPdf.write(eq(response), anyMap(), any(), any()));
    }

    private void assertNoLabelAccess() {
        verifyNoInteractions(preferences, programs);
        pdf.verifyNoInteractions();
    }
}
