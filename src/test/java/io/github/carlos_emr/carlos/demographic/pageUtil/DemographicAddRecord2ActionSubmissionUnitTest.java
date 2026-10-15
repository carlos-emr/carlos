/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.OtherIdDAO;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.waitinglist.WaitingList;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the one-patient-per-rendering guard of {@link DemographicAddRecord2Action}
 * (issue #4225): reloading the Add Record result page, or submitting the form twice, registers
 * one patient (a patient with no HIN has no other duplicate protection) and shows the first
 * registration's result page again.
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicAddRecord2Action repeated submissions")
class DemographicAddRecord2ActionSubmissionUnitTest extends CarlosUnitTestBase {

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final DemographicDao demographics = mock(DemographicDao.class);
    private final PatientConsentManager consents = mock(PatientConsentManager.class);
    private final ProgramDao programs = mock(ProgramDao.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final MockHttpSession session = new MockHttpSession();

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;
    private MockedStatic<OtherIdManager> otherIds;
    private MockedStatic<WaitingList> waitingLists;

    @BeforeEach
    void setUpAction() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(DemographicDao.class, demographics);
        registerMock(DemographicCustDao.class, mock(DemographicCustDao.class));
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));
        registerMock(DemographicArchiveDao.class, mock(DemographicArchiveDao.class));
        registerMock(DemographicExtArchiveDao.class, mock(DemographicExtArchiveDao.class));
        registerMock(WaitingListDao.class, mock(WaitingListDao.class));
        registerMock(PatientConsentManager.class, consents);
        registerMock(ProgramDao.class, programs);
        registerMock(AdmissionDao.class, mock(AdmissionDao.class));

        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenAnswer(inv -> request);
        servlet.when(ServletActionContext::getResponse).thenAnswer(inv -> response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(info);
        // OtherIdManager resolves its DAO when the class is first loaded.
        registerMock(OtherIdDAO.class, mock(OtherIdDAO.class));
        otherIds = mockStatic(OtherIdManager.class);
        WaitingList noWaitingList = mock(WaitingList.class);
        when(noWaitingList.getFound()).thenReturn(false);
        waitingLists = mockStatic(WaitingList.class);
        waitingLists.when(WaitingList::getInstance).thenReturn(noWaitingList);

        when(info.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), isNull())).thenReturn(true);
        when(consents.getActiveConsentTypes()).thenReturn(List.of());
        doAnswer(inv -> {
            inv.<Demographic>getArgument(0).setDemographicNo(321);
            return null;
        }).when(demographics).save(any(Demographic.class));
        session.setAttribute("user", "999998");
    }

    @AfterEach
    void tearDownAction() {
        for (MockedStatic<?> mocked : new MockedStatic<?>[]{waitingLists, otherIds, sessions, servlet}) {
            if (mocked != null) mocked.close();
        }
    }

    /** One POST of the Add Demographic form, as the browser sends it (or replays it on reload). */
    private String submit(String token, String lastName) {
        request = new MockHttpServletRequest();
        request.setSession(session);
        request.setMethod("POST");
        request.setParameter("last_name", lastName);
        request.setParameter("first_name", "Check");
        request.setParameter("sex", "F");
        request.setParameter("year_of_birth", "1990");
        request.setParameter("month_of_birth", "01");
        request.setParameter("date_of_birth", "15");
        request.setParameter("postal", "M5W 1E6");
        if (token != null) {
            request.setParameter(FormSubmissionGuard.PARAMETER, token);
        }
        response = new MockHttpServletResponse();
        return new DemographicAddRecord2Action().execute();
    }

    private String rendering() {
        return FormSubmissionGuard.issue(session, FormSubmissionGuard.Scope.DEMOGRAPHIC_ADD, null);
    }

    @Test
    void shouldRegisterOnce_whenTheResultPageIsReloaded() {
        String token = rendering();

        String first = submit(token, "FAKE-PW-RP");
        String reload = submit(token, "FAKE-PW-RP");

        assertThat(first).isEqualTo(ActionSupport.SUCCESS);
        assertThat(reload).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("demographicNo")).isEqualTo("321");
        assertThat(request.getAttribute("bufName")).isEqualTo("FAKE-PW-RP,Check");
        verify(demographics, times(1)).save(any(Demographic.class));
    }

    @Test
    void shouldRegisterAgain_forANewRenderingOfTheForm() {
        submit(rendering(), "FAKE-PW-A");
        submit(rendering(), "FAKE-PW-B");

        verify(demographics, times(2)).save(any(Demographic.class));
    }

    @Test
    void shouldKeepTheFormSubmittable_whenValidationRefusedIt() {
        String token = rendering();

        String refused = submit(token, "X".repeat(Demographic.LAST_NAME_MAX_LENGTH + 1));
        String corrected = submit(token, "FAKE-PW-FIXED");

        assertThat(refused).isEqualTo("validationError");
        assertThat(corrected).isEqualTo(ActionSupport.SUCCESS);
        verify(demographics, times(1)).save(any(Demographic.class));
    }

    @Test
    void shouldRefuseWithoutRegistering_forATokenThisSessionDidNotIssue() {
        String foreign = FormSubmissionGuard.issue(new MockHttpSession(), FormSubmissionGuard.Scope.DEMOGRAPHIC_ADD, null);

        String result = submit(foreign, "FAKE-PW-X");

        assertThat(result).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);
        assertThat(response.getStatus()).isEqualTo(409);
        verify(demographics, never()).save(any(Demographic.class));
        verify(demographics, never()).searchByHealthCard(anyString());
    }

    @Test
    void shouldRegisterAsBefore_whenThePostCarriesNoToken() {
        submit(null, "FAKE-PW-1");
        submit(null, "FAKE-PW-1");

        verify(demographics, times(2)).save(any(Demographic.class));
    }
}
