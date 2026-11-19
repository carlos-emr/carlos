/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.service.impl.DefaultNoteService;
import io.github.carlos_emr.carlos.casemgmt.web.NoteDisplay;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.encounter.data.EctFormData.PatientForm;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DefaultNoteServiceFormDatesUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldKeepFormsInTheTimeline_whenEditOrCreationDatesAreMissing() {
        var created = Date.valueOf("2026-03-09");
        var edited = Timestamp.valueOf("2026-03-10 11:45:00");
        var patientForms = List.of(
                new PatientForm("BPMH", 3, 101, null, null),
                new PatientForm("BPMH", 2, 101, Date.valueOf("2026-03-08"), edited),
                new PatientForm("BPMH", 1, 101, created, null));
        var service = new DefaultNoteService();
        var loggedIn = mock(LoggedInInfo.class);
        when(loggedIn.getLoggedInProviderNo()).thenReturn("test-provider");
        var manager = mock(CaseManagementManager.class);
        when(manager.filterNotes1(eq("test-provider"), anyCollection(), eq("test-program")))
                .thenAnswer(invocation -> invocation.getArgument(1));
        ReflectionTestUtils.setField(service, "caseManagementManager", manager);
        ReflectionTestUtils.setField(service, "caseManagementNoteDao", mock(CaseManagementNoteDAO.class));
        ReflectionTestUtils.setField(service, "billingONCHeader1Dao", mock(BillingONCHeader1Dao.class));
        registerMock(ProviderDao.class, mock(ProviderDao.class));

        var criteria = new NoteSelectionCriteria();
        criteria.setDemographicId(101);
        criteria.setUserRole("doctor");
        criteria.setUserName("test-user");
        criteria.setProgramId("test-program");
        try (var forms = mockStatic(EctFormData.class); var eForms = mockStatic(EFormUtil.class)) {
            forms.when(() -> EctFormData.getGroupedPatientFormsFromAllTables(101))
                    .thenAnswer(ignored -> new ArrayList<>(patientForms));
            eForms.when(() -> EFormUtil.listPatientEFormsNoData("101", "doctor,test-user"))
                    .thenReturn(new ArrayList<>());

            var allNotes = service.findNotes(loggedIn, criteria).getNotes();
            assertThat(allNotes).extracting(NoteDisplay::getNoteId).containsExactly(1, 2, 3);
            assertThat(allNotes).extracting(NoteDisplay::getObservationDate).containsExactly(created, edited, null);

            criteria.setStartDate(Date.valueOf("2026-03-08"));
            criteria.setEndDate(Date.valueOf("2026-03-11"));
            assertThat(service.findNotes(loggedIn, criteria).getNotes()).extracting(NoteDisplay::getNoteId)
                    .containsExactly(1, 2);

            criteria.setStartDate(created);
            criteria.setEndDate(null);
            assertThat(service.findNotes(loggedIn, criteria).getNotes()).extracting(NoteDisplay::getNoteId)
                    .containsExactly(2);

            criteria.setStartDate(null);
            criteria.setEndDate(edited);
            assertThat(service.findNotes(loggedIn, criteria).getNotes()).extracting(NoteDisplay::getNoteId)
                    .containsExactly(1);
        }
    }
}
