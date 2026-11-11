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
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.waitinglist.WaitingList;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Level;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
    private final RecordingTransactionManager transactions = new RecordingTransactionManager();

    /** A no-op transaction manager that records how each transaction ended. */
    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }

    private MockHttpServletRequest request;
    /** The Name Used the next {@link #submit} sends; none when null. */
    private String nameUsed;
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
        registerMock(PlatformTransactionManager.class, transactions);

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
        if (nameUsed != null) {
            request.setParameter("nameUsed", nameUsed);
        }
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
    void shouldKeepTheFormSpentAndLogTheGap_whenARelatedRowFailsAfterThePatientCommits() {
        DemographicCustDao custs = mock(DemographicCustDao.class);
        registerMock(DemographicCustDao.class, custs);
        doThrow(new IllegalStateException("custom row failed")).doNothing().when(custs).persist(any(DemographicCust.class));
        String token = rendering();

        try (LogCapture log = LogCapture.forLogger(DemographicAddRecord2Action.class)) {
            assertThatThrownBy(() -> submit(token, "FAKE-PW-TAIL")).isInstanceOf(IllegalStateException.class);

            assertThat(log.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage()).contains("patient 321 was registered");
            });
        }
        // The patient row exists, so the reload shows it rather than registering a second patient.
        String reload = submit(token, "FAKE-PW-TAIL");

        assertThat(reload).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("demographicNo")).isEqualTo("321");
        verify(demographics, times(1)).save(any(Demographic.class));
    }

    @Test
    void shouldShowTheSavedPatient_whenARepeatCarriesEditedEntries() {
        String token = rendering();
        submit(token, "FAKE-PW-SAVED");
        Demographic saved = new Demographic(321);
        saved.setLastName("FAKE-PW-SAVED");
        saved.setFirstName("Check");
        when(demographics.getDemographic("321")).thenReturn(saved);

        String repeat = submit(token, "FAKE-PW-EDITED");

        assertThat(repeat).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("bufName")).isEqualTo("FAKE-PW-SAVED,Check");
        verify(demographics, times(1)).save(any(Demographic.class));
    }

    @Test
    void shouldLeaveTheFormSubmittable_whenThePatientSaveRolledBack() {
        String token = rendering();
        doAnswer(inv -> {
            throw new IllegalStateException("database unavailable");
        }).doAnswer(inv -> {
            inv.<Demographic>getArgument(0).setDemographicNo(322);
            return null;
        }).when(demographics).save(any(Demographic.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> submit(token, "FAKE-PW-RETRY"))
                .isInstanceOf(IllegalStateException.class);
        String retry = submit(token, "FAKE-PW-RETRY");

        assertThat(retry).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("demographicNo")).isEqualTo("322");
        assertThat(transactions.rollbacks).isEqualTo(1);
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

    /** Issue #5128: the saved patient keeps Name Used whole in the alias; pref_name holds what fits. */
    private Demographic savedWithNameUsed(String value) {
        nameUsed = value;
        assertThat(submit(rendering(), "FAKE-NAMEUSED")).isEqualTo(ActionSupport.SUCCESS);
        ArgumentCaptor<Demographic> saved = ArgumentCaptor.forClass(Demographic.class);
        verify(demographics).save(saved.capture());
        return saved.getValue();
    }

    // getPrefName() answers the alias first, so read the value pref_name is written from.
    private static String storedPrefName(Demographic demographic) {
        try {
            java.lang.reflect.Field field = Demographic.class.getDeclaredField("prefName");
            field.setAccessible(true);
            return (String) field.get(demographic);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void shouldKeepNameUsedWholeInAlias_whenItIsThirtyOneCharacters() {
        String value = "A".repeat(30) + "B";

        Demographic saved = savedWithNameUsed(value);

        assertThat(saved.getAlias()).isEqualTo(value);
        assertThat(storedPrefName(saved)).isEqualTo("A".repeat(30));
    }

    @Test
    void shouldKeepNameUsedWholeInAlias_whenItIsSeventyCharacters() {
        String value = "C".repeat(Demographic.ALIAS_MAX_LENGTH);

        Demographic saved = savedWithNameUsed(value);

        assertThat(saved.getAlias()).isEqualTo(value);
        assertThat(storedPrefName(saved)).isEqualTo("C".repeat(Demographic.PREF_NAME_MAX_LENGTH));
    }

    @Test
    void shouldStoreEmptyPrefName_whenNoNameUsedIsSent() {
        Demographic saved = savedWithNameUsed(null);

        assertThat(storedPrefName(saved)).isEmpty();
    }
}
