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
package io.github.carlos_emr.carlos.encounter.immunization.pageUtil;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ConfigImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.ImmunizationsDao;
import io.github.carlos_emr.carlos.commn.model.ConfigImmunization;
import io.github.carlos_emr.carlos.commn.model.Immunizations;
import io.github.carlos_emr.carlos.encounter.immunization.config.data.EctImmImmunizationSetWriter;
import io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmCreateImmunizationSetConfig2Action;
import io.github.carlos_emr.carlos.encounter.immunization.data.EctImmImmunizationData;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.UtilMisc;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.concurrent.Callable;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The legacy immunization schedule writes the patient its own request names (issue #4673).
 * Adding a template ({@code saveConfig}) used to write into the patient of the login-wide
 * {@code EctSessionBean}, whichever chart was entered last in any window, and record that entry's
 * provider; deleting or restoring a set ({@code deleteSchedule}) posted its patient under a name
 * the redisplayed schedule did not read, so the page fell back to that bean and showed the other
 * patient's schedule. Creating a template recorded the bean's provider as its author. Saving the
 * schedule ({@code saveSchedule}) now checks its patient as the other two writes do.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("prevention")
@DisplayName("Immunization schedule patient isolation")
class ImmunizationSchedulePatientIsolationUnitTest extends CarlosUnitTestBase {
    private static final String A = "900001";
    private static final String B = "900002";

    private SecurityInfoManager security;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private EctSessionBean other;

    @BeforeEach
    void enterAnotherChartLast() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        // EctImmImmunizationData resolves these in its static initializer.
        createAndRegisterMock(ImmunizationsDao.class);
        createAndRegisterMock(ProviderDao.class);
        ConfigImmunizationDao templates = createAndRegisterMock(ConfigImmunizationDao.class);
        ConfigImmunization template = new ConfigImmunization();
        template.setXmlDoc("<immunizationSet name=\"Fixture set\" headers=\"true\"><columnList/><rowList/></immunizationSet>");
        when(templates.find(11)).thenReturn(template);

        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(login), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login), anyInt())).thenReturn(true);

        MockHttpSession session = new MockHttpSession();
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        other = new EctSessionBean();
        other.demographicNo = B;
        other.providerNo = "222222";
        session.setAttribute("EctSessionBean", other);
        request = new MockHttpServletRequest();
        request.setSession(session);
        request.setMethod("POST");
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("should add the chosen template to the requested patient's schedule, by the logged-in provider")
    void shouldSaveConfigToRequestedPatient_whenAnotherChartWasEnteredLast() throws Exception {
        request.setParameter("demographic_no", A);
        request.setParameter("chkSet", "11");

        try (MockedConstruction<EctImmImmunizationData> schedules = mockConstruction(EctImmImmunizationData.class,
                (data, context) -> when(data.saveImmunizations(anyString(), anyString(), anyString(), anyInt()))
                        .thenReturn(true))) {
            assertThat(run(() -> new EctImmSaveConfig2Action().execute())).isEqualTo(ActionSupport.SUCCESS);

            EctImmImmunizationData data = schedules.constructed().getFirst();
            verify(data).getCurrentSchedule(A);
            verify(data).saveImmunizations(eq(A), eq("999998"), contains("Fixture set"), eq(0));
            verify(data, never()).getCurrentSchedule(B);
            verify(data, never()).saveImmunizations(eq(B), anyString(), anyString(), anyInt());
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"missing", "conflicting", "legacy demoNo only"})
    @DisplayName("should refuse to add a template without one unambiguous patient")
    void shouldRejectSaveConfig_whenPatientIsMissingOrConflicting(String variant) throws Exception {
        request.setParameter("chkSet", "11");
        switch (variant) {
            case "conflicting" -> {
                request.setParameter("demographic_no", A);
                request.setParameter("demographicNo", B);
            }
            case "legacy demoNo only" -> request.setParameter("demoNo", A);
            default -> {
                // "missing": the request names no patient at all.
            }
        }

        try (MockedConstruction<EctImmImmunizationData> schedules = mockConstruction(EctImmImmunizationData.class)) {
            assertThat(run(() -> new EctImmSaveConfig2Action().execute())).isEqualTo(ActionSupport.NONE);

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(schedules.constructed()).isEmpty();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"patient write privilege", "patient record access"})
    @DisplayName("should refuse to add a template to a patient the user may not write or open")
    void shouldRefuseSaveConfig_whenPatientAccessIsDenied(String deniedBy) {
        request.setParameter("demographic_no", A);
        request.setParameter("chkSet", "11");
        denyPatientAccess(deniedBy);

        try (MockedConstruction<EctImmImmunizationData> schedules = mockConstruction(EctImmImmunizationData.class)) {
            assertThatThrownBy(() -> run(() -> new EctImmSaveConfig2Action().execute()))
                    .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_demographic)");
            assertThat(schedules.constructed()).isEmpty();
        }
    }

    @Test
    @DisplayName("should delete a set in the requested patient's schedule and redisplay that patient")
    void shouldDeleteSetOfRequestedPatient_whenAnotherChartWasEnteredLast() throws Exception {
        request.setParameter("demographic_no", A);
        request.setParameter("method", "delete");
        request.setParameter("tblSet", "0");
        request.setParameter("scheduleVersion", "5");
        Immunizations current = new Immunizations();
        current.setId(5);
        current.setImmunizations("<immunizations><immunizationSet name=\"Fixture set\"/></immunizations>");

        try (MockedConstruction<EctImmImmunizationData> schedules = mockConstruction(EctImmImmunizationData.class,
                (data, context) -> {
                    when(data.getCurrentSchedule(A)).thenReturn(current);
                    when(data.saveImmunizations(anyString(), anyString(), anyString(), anyInt())).thenReturn(true);
                })) {
            assertThat(run(() -> new EctImmDeleteImmSchedule2Action().execute())).isEqualTo(ActionSupport.SUCCESS);

            EctImmImmunizationData data = schedules.constructed().getFirst();
            verify(data).saveImmunizations(eq(A), eq("999998"), contains("status=\"deleted\""), eq(5));
            verify(data, never()).getCurrentSchedule(B);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"missing", "conflicting", "legacy demoNo only"})
    @DisplayName("should refuse to delete a set without one unambiguous patient")
    void shouldRejectDelete_whenPatientIsMissingOrConflicting(String variant) throws Exception {
        request.setParameter("method", "delete");
        request.setParameter("tblSet", "0");
        request.setParameter("scheduleVersion", "5");
        switch (variant) {
            case "conflicting" -> request.setParameter("demographic_no", A, B);
            case "legacy demoNo only" -> request.setParameter("demoNo", A);
            default -> {
                // "missing": the request names no patient at all.
            }
        }

        try (MockedConstruction<EctImmImmunizationData> schedules = mockConstruction(EctImmImmunizationData.class)) {
            assertThat(run(() -> new EctImmDeleteImmSchedule2Action().execute())).isEqualTo(ActionSupport.NONE);

            assertThat(response.getStatus()).isEqualTo(400);
            for (EctImmImmunizationData data : schedules.constructed()) {
                verify(data, never()).saveImmunizations(anyString(), anyString(), anyString(), anyInt());
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"patient write privilege", "patient record access"})
    @DisplayName("should refuse to delete a set of a patient the user may not write or open")
    void shouldRefuseDelete_whenPatientAccessIsDenied(String deniedBy) {
        request.setParameter("demographic_no", A);
        request.setParameter("method", "delete");
        request.setParameter("tblSet", "0");
        request.setParameter("scheduleVersion", "5");
        denyPatientAccess(deniedBy);

        try (MockedConstruction<EctImmImmunizationData> schedules = mockConstruction(EctImmImmunizationData.class)) {
            assertThatThrownBy(() -> run(() -> new EctImmDeleteImmSchedule2Action().execute()))
                    .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_demographic)");
            for (EctImmImmunizationData data : schedules.constructed()) {
                verify(data, never()).saveImmunizations(anyString(), anyString(), anyString(), anyInt());
            }
        }
    }

    @Test
    @DisplayName("should save the schedule of the requested patient, by the logged-in provider")
    void shouldSaveScheduleOfRequestedPatient_whenAnotherChartWasEnteredLast() throws Exception {
        prepareScheduleSave();
        request.setParameter("demographic_no", A);

        try (MockedConstruction<EctImmImmunizationData> schedules = mockConstruction(EctImmImmunizationData.class,
                (data, context) -> when(data.saveImmunizations(anyString(), anyString(), anyString(), anyInt()))
                        .thenReturn(true))) {
            assertThat(run(() -> new EctImmSaveSchedule2Action().execute())).isEqualTo("reload");

            EctImmImmunizationData data = schedules.constructed().getFirst();
            verify(data).saveImmunizations(eq(A), eq("999998"), contains("lot=\"FAKE-LOT\""), eq(5));
            verify(data, never()).saveImmunizations(eq(B), anyString(), anyString(), anyInt());
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"missing", "conflicting", "legacy demoNo only"})
    @DisplayName("should refuse to save a schedule without one unambiguous patient")
    void shouldRejectSaveSchedule_whenPatientIsMissingOrConflicting(String variant) throws Exception {
        prepareScheduleSave();
        switch (variant) {
            case "conflicting" -> request.setParameter("demographic_no", A, B);
            case "legacy demoNo only" -> request.setParameter("demoNo", A);
            default -> {
                // "missing": the request names no patient at all.
            }
        }

        try (MockedConstruction<EctImmImmunizationData> schedules = mockConstruction(EctImmImmunizationData.class)) {
            assertThat(run(() -> new EctImmSaveSchedule2Action().execute())).isEqualTo(ActionSupport.NONE);

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(schedules.constructed()).isEmpty();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"patient write privilege", "patient record access"})
    @DisplayName("should refuse to save the schedule of a patient the user may not write or open")
    void shouldRefuseSaveSchedule_whenPatientAccessIsDenied(String deniedBy) {
        prepareScheduleSave();
        request.setParameter("demographic_no", A);
        denyPatientAccess(deniedBy);

        try (MockedConstruction<EctImmImmunizationData> schedules = mockConstruction(EctImmImmunizationData.class)) {
            assertThatThrownBy(() -> run(() -> new EctImmSaveSchedule2Action().execute()))
                    .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_demographic)");
            assertThat(schedules.constructed()).isEmpty();
        }
    }

    @Test
    @DisplayName("should record the logged-in provider as a new template's author, not another chart's entry provider")
    void shouldAuthorTemplateAsLoggedInProvider_whenAnotherChartWasEnteredLast() throws Exception {
        request.setParameter("heading0D1", "Dose 1");
        request.setParameter("immunization1D0", "Fixture vaccine");

        try (MockedConstruction<EctImmImmunizationSetWriter> writers = mockConstruction(EctImmImmunizationSetWriter.class)) {
            EctImmCreateImmunizationSetConfig2Action action = run(EctImmCreateImmunizationSetConfig2Action::new);
            action.setName("Fixture template");
            assertThat(run(action::execute)).isEqualTo(ActionSupport.SUCCESS);

            verify(writers.constructed().getFirst()).doWrite(anyInt(), any(), any(), any(), eq("Fixture template"), eq("999998"));
        }
    }

    @Test
    @DisplayName("should create a template when no chart was ever entered in this login")
    void shouldAuthorTemplate_whenNoChartWasEntered() throws Exception {
        request.getSession().removeAttribute("EctSessionBean");
        request.setParameter("heading0D1", "Dose 1");
        request.setParameter("immunization1D0", "Fixture vaccine");

        try (MockedConstruction<EctImmImmunizationSetWriter> writers = mockConstruction(EctImmImmunizationSetWriter.class)) {
            EctImmCreateImmunizationSetConfig2Action action = run(EctImmCreateImmunizationSetConfig2Action::new);
            action.setName("Fixture template");
            assertThat(run(action::execute)).isEqualTo(ActionSupport.SUCCESS);

            verify(writers.constructed().getFirst()).doWrite(anyInt(), any(), any(), any(), eq("Fixture template"), eq("999998"));
        }
    }

    /** Runs with the servlet context bound to this test's request, as Struts binds it. */
    private <T> T run(Callable<T> body) throws Exception {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            return body.call();
        }
    }

    /** Schedule.jsp's save post, but for its patient: one set, one row, one dose with a lot. */
    private void prepareScheduleSave() {
        request.setParameter("scheduleVersion", "5");
        request.setParameter("xmlDoc", UtilMisc.encode64("<immunizations><immunizationSet name=\"Fixture set\">"
                + "<row name=\"Fixture vaccine\"><cell index=\"0\"/></row></immunizationSet></immunizations>"));
        request.setParameter("tdSet0_Row0_Col0_lot", "FAKE-LOT");
    }

    /** Each half of the actions' patient check refuses on its own. */
    private void denyPatientAccess(String deniedBy) {
        if ("patient write privilege".equals(deniedBy)) {
            when(security.hasPrivilege(login, "_demographic", "w", A)).thenReturn(false);
        } else {
            when(security.isAllowedAccessToPatientRecord(login, Integer.parseInt(A))).thenReturn(false);
        }
    }

}
