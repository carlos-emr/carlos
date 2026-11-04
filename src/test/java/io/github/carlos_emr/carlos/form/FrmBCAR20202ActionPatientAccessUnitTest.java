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
package io.github.carlos_emr.carlos.form;

import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.PrintResourceLogDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020Dao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020DataDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020TextDao;
import io.github.carlos_emr.carlos.form.model.FormBCAR2020;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BC-AR 2020's save and print require the form rights and the rights to the named patient's record, and only accept
 * a stored form that belongs to that patient.
 *
 * @since 2026-10-18
 */
@DisplayName("BC-AR 2020 save and print: patient rights and the form's patient")
@Tag("unit")
@Tag("form")
@Tag("security")
class FrmBCAR20202ActionPatientAccessUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 42;
    private static final int OTHER_PATIENT = 43;
    private static final int OWN_FORM = 7;
    private static final int OTHER_PATIENTS_FORM = 8;

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final FormBCAR2020Dao formDao = mock(FormBCAR2020Dao.class);
    private final FormBCAR2020DataDao dataDao = mock(FormBCAR2020DataDao.class);
    private final FormBCAR2020TextDao textDao = mock(FormBCAR2020TextDao.class);
    private final PrintResourceLogDao printLogDao = mock(PrintResourceLogDao.class);

    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(DemographicDao.class, mock(DemographicDao.class));
        registerMock(FormBCAR2020Dao.class, formDao);
        registerMock(FormBCAR2020DataDao.class, dataDao);
        registerMock(FormBCAR2020TextDao.class, textDao);
        registerMock(PrintResourceLogDao.class, printLogDao);
        // FrmBCAR2020Record's constructor (through FrmRecordFactory) looks these up.
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));

        // The action reads the request and response in its field initialisers, so these come first.
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(loggedInInfo);

        // The database assigns the new version's id; the model has no setter.
        doAnswer(call -> {
            FormBCAR2020 created = call.getArgument(0);
            ReflectionTestUtils.setField(created, "formId", 9);
            return null;
        }).when(formDao).persist(any(FormBCAR2020.class));
        when(formDao.find(OWN_FORM)).thenReturn(form(PATIENT));
        when(formDao.find(OTHER_PATIENTS_FORM)).thenReturn(form(OTHER_PATIENT));
        when(dataDao.findFields(anyInt())).thenReturn(List.of());
        when(textDao.findFields(anyInt())).thenReturn(List.of());
        when(request.getParameterMap()).thenReturn(Map.of());
        when(request.getParameter("demographicNo")).thenReturn(String.valueOf(PATIENT));
        when(request.getParameter("provNo")).thenReturn("999998");
    }

    @AfterEach
    void tearDown() {
        loggedInInfoStatic.close();
        servletActionContext.close();
    }

    private static FormBCAR2020 form(int demographicNo) {
        FormBCAR2020 form = new FormBCAR2020();
        form.setDemographicNo(demographicNo);
        return form;
    }

    private void grant(String object, String rights, boolean granted) {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq(object), eq(rights), eq(PATIENT))).thenReturn(granted);
    }

    @Test
    void shouldSaveANewVersion_whenTheRightsAreHeldAndTheFormIsThePatients() throws Exception {
        grant("_form", "w", true);
        grant("_demographic", "w", true);
        when(request.getParameter("formId")).thenReturn(String.valueOf(OWN_FORM));

        assertThat(new FrmBCAR20202Action().save()).isEqualTo("none");

        verify(formDao).persist(any(FormBCAR2020.class));
    }

    @Test
    void shouldStoreTheFormAsTheSignedInProviders_notTheRequestsProvider() throws Exception {
        grant("_form", "w", true);
        grant("_demographic", "w", true);
        when(request.getParameter("formId")).thenReturn("0");
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("111111");
        // The request names another provider (setUp sends provNo=999998).

        new FrmBCAR20202Action().save();

        ArgumentCaptor<FormBCAR2020> stored = ArgumentCaptor.forClass(FormBCAR2020.class);
        verify(formDao).persist(stored.capture());
        assertThat(stored.getValue().getProviderNo()).isEqualTo("111111");
        verify(response).sendRedirect(org.mockito.ArgumentMatchers.contains("provNo=111111"));
    }

    @Test
    void shouldRefuseTheSave_withoutTheFormRights() {
        grant("_form", "w", false);
        grant("_demographic", "w", true);
        when(request.getParameter("formId")).thenReturn("0");

        assertThatThrownBy(() -> new FrmBCAR20202Action().save())
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_form)");
        verifyNoInteractions(dataDao, textDao);
        verify(formDao, never()).persist(any());
    }

    @Test
    void shouldRefuseTheSave_whenThePatientsRecordIsRestricted() {
        // A per-patient restriction is held on _demographic$N; _form alone does not see it.
        grant("_form", "w", true);
        grant("_demographic", "w", false);
        when(request.getParameter("formId")).thenReturn("0");

        assertThatThrownBy(() -> new FrmBCAR20202Action().save())
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_demographic)");
        verifyNoInteractions(dataDao, textDao);
        verify(formDao, never()).persist(any());
    }

    @Test
    void shouldRefuseTheSave_whenTheEarlierVersionIsAnotherPatients() {
        grant("_form", "w", true);
        grant("_demographic", "w", true);
        when(request.getParameter("formId")).thenReturn(String.valueOf(OTHER_PATIENTS_FORM));

        assertThatThrownBy(() -> new FrmBCAR20202Action().save()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(dataDao, textDao);
        verify(formDao, never()).persist(any());
    }

    @Test
    void shouldRefuseThePrint_whenTheFormIsAnotherPatients() {
        grant("_form", "r", true);
        grant("_demographic", "r", true);
        when(request.getParameter("formId")).thenReturn(String.valueOf(OTHER_PATIENTS_FORM));

        assertThatThrownBy(() -> new FrmBCAR20202Action().print()).isInstanceOf(SecurityException.class);
        verify(response, never()).setContentType(anyString());
        verifyNoInteractions(printLogDao, dataDao, textDao);
    }

    @Test
    void shouldRefuseThePrint_whenTheFormDoesNotExist() {
        grant("_form", "r", true);
        grant("_demographic", "r", true);
        when(request.getParameter("formId")).thenReturn("999");

        assertThatThrownBy(() -> new FrmBCAR20202Action().print()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(printLogDao);
    }

    @Test
    void shouldRefuseThePrint_withoutTheRightsToThePatientsRecord() {
        grant("_form", "r", true);
        grant("_demographic", "r", false);
        when(request.getParameter("formId")).thenReturn(String.valueOf(OWN_FORM));

        assertThatThrownBy(() -> new FrmBCAR20202Action().print())
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_demographic)");
        verify(response, never()).setContentType(anyString());
        verifyNoInteractions(printLogDao);
    }

    @Test
    void shouldRefuseThePrint_withoutTheFormRights() {
        grant("_form", "r", false);
        grant("_demographic", "r", true);
        when(request.getParameter("formId")).thenReturn(String.valueOf(OWN_FORM));

        assertThatThrownBy(() -> new FrmBCAR20202Action().print())
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_form)");
        verifyNoInteractions(printLogDao);
    }
}
