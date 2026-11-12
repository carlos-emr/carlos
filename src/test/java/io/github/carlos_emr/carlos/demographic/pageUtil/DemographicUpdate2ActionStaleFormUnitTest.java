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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.OtherIdDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.demographic.pageUtil.DemographicEditVersion.Difference;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.provider.model.PreventionManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.waitinglist.WaitingList;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the stale-form protection of {@link DemographicUpdate2Action} (issue #4225): a
 * Master Record form rendered before another user's save is refused with HTTP 409 and writes
 * nothing, a current form saves, and extension rows are matched to the patient's rows by key.
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicUpdate2Action stale forms")
class DemographicUpdate2ActionStaleFormUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 123;

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final DemographicDao demographics = mock(DemographicDao.class);
    private final DemographicExtDao extensions = mock(DemographicExtDao.class);
    private final DemographicCustDao customs = mock(DemographicCustDao.class);
    private final DemographicArchiveDao archives = mock(DemographicArchiveDao.class);
    private final DemographicExtArchiveDao extensionArchives = mock(DemographicExtArchiveDao.class);
    private final PatientConsentManager consents = mock(PatientConsentManager.class);
    private final PreventionManager preventions = mock(PreventionManager.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final RecordingTransactionManager transactions = new RecordingTransactionManager();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;
    private MockedStatic<OtherIdManager> otherIds;
    private MockedStatic<WaitingList> waitingLists;

    /** A no-op transaction manager that records how each transaction ended. */
    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }

    @BeforeEach
    void setUpAction() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(DemographicDao.class, demographics);
        registerMock(DemographicExtDao.class, extensions);
        registerMock(DemographicCustDao.class, customs);
        registerMock(DemographicArchiveDao.class, archives);
        registerMock(DemographicExtArchiveDao.class, extensionArchives);
        registerMock(WaitingListDao.class, mock(WaitingListDao.class));
        registerMock(OscarAppointmentDao.class, mock(OscarAppointmentDao.class));
        registerMock(PatientConsentManager.class, consents);
        registerMock(PreventionManager.class, preventions);
        registerMock(PlatformTransactionManager.class, transactions);

        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
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
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), nullable(String.class))).thenReturn(true);
        when(consents.getActiveConsentTypes()).thenReturn(List.of());
        when(archives.archiveRecord(any(Demographic.class))).thenReturn(5L);

        request.setMethod("POST");
        request.getSession().setAttribute("user", "999998");
        request.setParameter("demographic_no", String.valueOf(PATIENT));
        request.setParameter("last_name", "FAKE-PATIENT");
        request.setParameter("first_name", "Check");
        request.setParameter("city", "CITY-A");
        request.setParameter("phone", "");
        request.setParameter("demo_cell", "555-0100");
        // A form rendered before the extension rows existed, or a tampered one, names another id.
        request.setParameter("demo_cell_id", "999");
        request.setParameter("CSRF-TOKEN", "token-from-the-form");
    }

    @AfterEach
    void tearDownAction() {
        for (MockedStatic<?> mocked : new MockedStatic<?>[]{waitingLists, otherIds, sessions, servlet}) {
            if (mocked != null) mocked.close();
        }
    }

    /** The patient as the database holds it; each call returns a fresh copy, as a new read would. */
    private static Demographic stored(String phone) {
        Demographic demographic = new Demographic(PATIENT);
        demographic.setLastName("FAKE-PATIENT");
        demographic.setFirstName("Check");
        demographic.setCity("Toronto");
        demographic.setPhone(phone);
        demographic.setLastUpdateUser("999998");
        demographic.setLastUpdateDate(new Date(1_700_000_000_000L));
        return demographic;
    }

    private static Map<String, String> storedExtensions() {
        Map<String, String> rows = new HashMap<>();
        rows.put("demo_cell", "555-0100");
        rows.put("demo_cell_id", "77");
        return rows;
    }

    private static DemographicCust storedCust() {
        DemographicCust cust = new DemographicCust();
        cust.setId(PATIENT);
        cust.setAlert("");
        cust.setNotes("<unotes></unotes>");
        return cust;
    }

    /** The database now: session B saved the phone number after session A's form was rendered. */
    private void databaseAfterAnotherSave() {
        when(demographics.getDemographic(String.valueOf(PATIENT))).thenAnswer(inv -> stored("555-0187"));
        when(demographics.findForUpdate(PATIENT)).thenAnswer(inv -> stored("555-0187"));
        when(extensions.getAllValuesForDemo(PATIENT)).thenAnswer(inv -> storedExtensions());
        when(customs.find(PATIENT)).thenAnswer(inv -> storedCust());
        DemographicExt cell = new DemographicExt(77, "999998", PATIENT, "demo_cell", "555-0100");
        when(extensions.getDemographicExtByDemographicNo(PATIENT)).thenReturn(new ArrayList<>(List.of(cell)));
    }

    @Test
    void shouldRefuseAndWriteNothing_whenTheFormWasRenderedBeforeAnotherSave() throws Exception {
        String renderedVersion = DemographicEditVersion.of(stored(""), storedExtensions(), storedCust());
        databaseAfterAnotherSave();
        request.setParameter(DemographicEditVersion.PARAMETER, renderedVersion);

        String result = new DemographicUpdate2Action().execute();

        assertThat(result).isEqualTo("conflict");
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verify(demographics, never()).save(any(Demographic.class));
        verify(extensions, never()).saveEntity(any(DemographicExt.class));
        verify(archives, never()).archiveRecord(any(Demographic.class));
        verify(customs, never()).merge(any(DemographicCust.class));
        verify(consents, never()).getActiveConsentTypes();
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
    }

    @Test
    void shouldKeepTheRefusedEntriesAndTheCurrentValues_forTheConflictPage() throws Exception {
        String renderedVersion = DemographicEditVersion.of(stored(""), storedExtensions(), storedCust());
        databaseAfterAnotherSave();
        request.setParameter(DemographicEditVersion.PARAMETER, renderedVersion);

        new DemographicUpdate2Action().execute();

        @SuppressWarnings("unchecked")
        List<Difference> differences = (List<Difference>) request.getAttribute("conflictDifferences");
        assertThat(differences).extracting(Difference::labelKey, Difference::yours, Difference::current)
                .contains(org.assertj.core.groups.Tuple.tuple("demographic.demographiceditdemographic.formCity", "CITY-A", "Toronto"),
                        org.assertj.core.groups.Tuple.tuple("demographic.demographiceditdemographic.formPhoneH", "", "555-0187"));
        assertThat(request.getAttribute("conflictVersion"))
                .isEqualTo(DemographicEditVersion.of(stored("555-0187"), storedExtensions(), storedCust()));
        @SuppressWarnings("unchecked")
        List<String[]> draft = (List<String[]>) request.getAttribute("conflictDraft");
        assertThat(draft).extracting(entry -> entry[0])
                .contains("city", "demo_cell", "demographic_no")
                .doesNotContain("CSRF-TOKEN", DemographicEditVersion.PARAMETER);
        assertThat(draft).anySatisfy(entry -> assertThat(entry).containsExactly("city", "CITY-A"));
    }

    @Test
    void shouldSaveUnderTheLock_whenTheFormIsCurrent() throws Exception {
        databaseAfterAnotherSave();
        request.setParameter("phone", "555-0187");
        request.setParameter(DemographicEditVersion.PARAMETER,
                DemographicEditVersion.of(stored("555-0187"), storedExtensions(), storedCust()));

        String result = new DemographicUpdate2Action().execute();

        assertThat(result).isNull();
        assertThat(response.getRedirectedUrl()).endsWith("/demographic/DemographicEdit?demographic_no=" + PATIENT);
        ArgumentCaptor<Demographic> saved = ArgumentCaptor.forClass(Demographic.class);
        verify(demographics).save(saved.capture());
        assertThat(saved.getValue().getCity()).isEqualTo("CITY-A");
        assertThat(saved.getValue().getPhone()).isEqualTo("555-0187");
        verify(demographics).findForUpdate(PATIENT);
        assertThat(transactions.commits).isEqualTo(1);
    }

    @Test
    void shouldMatchExtensionRowsByKey_neverByThePostedId() throws Exception {
        databaseAfterAnotherSave();

        new DemographicUpdate2Action().execute();

        ArgumentCaptor<DemographicExt> saved = ArgumentCaptor.forClass(DemographicExt.class);
        verify(extensions, atLeastOnce()).saveEntity(saved.capture());
        assertThat(saved.getAllValues()).filteredOn(extension -> "demo_cell".equals(extension.getKey()))
                .singleElement().satisfies(extension -> {
                    assertThat(extension.getId()).isEqualTo(77);
                    assertThat(extension.getDemographicNo()).isEqualTo(PATIENT);
                    assertThat(extension.getValue()).isEqualTo("555-0100");
                });
        // A key the patient has no row for yet is inserted once, as a new row.
        assertThat(saved.getAllValues()).filteredOn(extension -> "hPhoneExt".equals(extension.getKey()))
                .singleElement().satisfies(extension -> assertThat(extension.getId()).isNull());
    }

    @Test
    void shouldWriteTheFormOntoTheLockedRow_keepingColumnsTheFormDoesNotWrite() throws Exception {
        databaseAfterAnotherSave();
        // Read before the lock, the copy still had the old value; the locked row has the newer one.
        when(demographics.getDemographic(String.valueOf(PATIENT))).thenAnswer(inv -> {
            Demographic before = stored("555-0187");
            before.setPatientType("OLD");
            return before;
        });
        Demographic locked = stored("555-0187");
        locked.setPatientType("NEWER");
        when(demographics.findForUpdate(PATIENT)).thenReturn(locked);

        new DemographicUpdate2Action().execute();

        ArgumentCaptor<Demographic> saved = ArgumentCaptor.forClass(Demographic.class);
        verify(demographics).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(locked);
        assertThat(saved.getValue().getPatientType()).isEqualTo("NEWER");
        assertThat(saved.getValue().getCity()).isEqualTo("CITY-A");
        verify(archives).archiveRecord(locked);
    }

    @Test
    void shouldRefuseBeforeReadingThePatient_whenTheUserIsKeptFromThisRecord() {
        databaseAfterAnotherSave();
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq(String.valueOf(PATIENT))))
                .thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new DemographicUpdate2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");
        verify(demographics, never()).getDemographic(anyString());
        verify(demographics, never()).findForUpdate(any());
    }

    @Test
    void shouldSaveWithoutTheCheck_whenTheFormCarriesNoVersion() throws Exception {
        databaseAfterAnotherSave();

        String result = new DemographicUpdate2Action().execute();

        assertThat(result).isNull();
        verify(demographics).save(any(Demographic.class));
        verify(extensions, never()).getAllValuesForDemo(anyInt());
    }

    @Test
    void shouldWriteNothing_whenThePatientRowIsGoneAtTheLock() throws Exception {
        databaseAfterAnotherSave();
        when(demographics.findForUpdate(PATIENT)).thenReturn(null);

        String result = new DemographicUpdate2Action().execute();

        assertThat(result).isEqualTo("error");
        verify(demographics, never()).save(any(Demographic.class));
        verify(extensions, never()).saveEntity(any(DemographicExt.class));
        verify(preventions, never()).removePrevention(anyString());
    }
}
