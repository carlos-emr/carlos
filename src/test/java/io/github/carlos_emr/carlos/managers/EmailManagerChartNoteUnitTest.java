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
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.email.core.EmailConsentResolver;
import io.github.carlos_emr.carlos.email.core.EmailSenderFactory;
import io.github.carlos_emr.carlos.email.util.EmailNoteUtil;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

/**
 * The {@code addEmailNote} overload that records an email on the chart with caller-supplied text, used
 * for patient portal invitations, whose body carries an account credential that must never reach a
 * permanent chart note.
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailManager chart note with caller text")
class EmailManagerChartNoteUnitTest extends CarlosUnitTestBase {

    private EmailManager emailManager;
    private SecurityInfoManager securityInfoManager;
    private CaseManagementManager caseManagementManager;
    private LoggedInInfo loggedInInfo;
    private EmailLog emailLog;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        caseManagementManager = mock(CaseManagementManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        emailManager = new EmailManager(mock(EmailConsentResolver.class), new EmailSenderFactory(),
                securityInfoManager, mock(OutboundEmailArchiveService.class));
        injectDependency(emailManager, "caseManagementManager", caseManagementManager);
        injectDependency(emailManager, "programManager", mock(ProgramManager.class));
        SecRole doctor = new SecRole();
        injectDependency(doctor, "id", 3);
        when(caseManagementManager.getSecRoleByRoleName("doctor")).thenReturn(doctor);
        when(caseManagementManager.saveNoteSimpleReturnID(any(CaseManagementNote.class))).thenReturn(81L);

        Demographic patient = new Demographic();
        patient.setDemographicNo(123);
        emailLog = new EmailLog();
        injectDependency(emailLog, "id", 77);
        emailLog.setDemographic(patient);
        emailLog.setBody("Enter this invitation code: invite-code-Xy7");
    }

    @Test
    @DisplayName("should save exactly the given text, never the email body, and link it to the email")
    void shouldSaveGivenText_insteadOfTheBody() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_email"), anyString(),
                nullable(String.class))).thenReturn(true);

        try (MockedConstruction<EctProgram> programs = mockConstruction(EctProgram.class,
                (program, context) -> when(program.getProgram("999998")).thenReturn("10016"));
                MockedConstruction<EmailNoteUtil> fullNotes = mockConstruction(EmailNoteUtil.class)) {
            emailManager.addEmailNote(loggedInInfo, emailLog, "Patient portal invitation emailed.");

            assertThat(fullNotes.constructed()).isEmpty();
        }

        ArgumentCaptor<CaseManagementNote> note = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(caseManagementManager).saveNoteSimpleReturnID(note.capture());
        assertThat(note.getValue().getNote()).isEqualTo("Patient portal invitation emailed.");
        assertThat(note.getValue().getNote()).doesNotContain("invite-code-Xy7");
        assertThat(note.getValue().getDemographic_no()).isEqualTo("123");
        assertThat(note.getValue().isSigned()).isTrue();
        ArgumentCaptor<CaseManagementNoteLink> link = ArgumentCaptor.forClass(CaseManagementNoteLink.class);
        verify(caseManagementManager).saveNoteLink(link.capture());
        assertThat(link.getValue().getTableId()).isEqualTo(77L);
        assertThat(link.getValue().getNoteId()).isEqualTo(81L);
    }

    @Test
    @DisplayName("should still write the note for a provider with no program, under the doctor role")
    void shouldWriteNote_forProviderWithoutProgram() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_email"), anyString(),
                nullable(String.class))).thenReturn(true);
        ProgramManager programManager = mock(ProgramManager.class);
        injectDependency(emailManager, "programManager", programManager);

        try (MockedConstruction<EctProgram> programs = mockConstruction(EctProgram.class,
                (program, context) -> when(program.getProgram("999998")).thenReturn("0"))) {
            emailManager.addEmailNote(loggedInInfo, emailLog, "Patient portal invitation emailed.");
        }

        verifyNoInteractions(programManager);
        ArgumentCaptor<CaseManagementNote> note = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(caseManagementManager).saveNoteSimpleReturnID(note.capture());
        assertThat(note.getValue().getReporter_caisi_role()).isEqualTo("3");
        assertThat(note.getValue().getProgram_no()).isEqualTo("0");
    }

    @Test
    @DisplayName("should refuse a caller without email read rights before writing anything")
    void shouldRefuse_withoutEmailReadPrivilege() {
        assertThatThrownBy(() -> emailManager.addEmailNote(loggedInInfo, emailLog, "any text"))
                .hasMessage("missing required sec object (_email)");
        verifyNoInteractions(caseManagementManager);
    }
}
