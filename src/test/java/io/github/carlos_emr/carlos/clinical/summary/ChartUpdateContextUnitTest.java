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
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChartUpdateContextUnitTest extends CarlosUnitTestBase {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final DocumentManager documents = mock(DocumentManager.class);
    private final CaseManagementManager notes = mock(CaseManagementManager.class);
    private final TicklerDao ticklers = mock(TicklerDao.class);
    private final ProgramProviderDAO programs = mock(ProgramProviderDAO.class);
    private final ProgramManager programManager = mock(ProgramManager.class);
    private final TicklerManager ticklerAccess = mock(TicklerManager.class);
    private final EntityManager em = mock(EntityManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final MockHttpSession session = new MockHttpSession();
    private final ChartUpdateChartRecords records = mock(ChartUpdateChartRecords.class);
    private final ChartUpdateContext context = new ChartUpdateContext(security, documents, notes, ticklers, programs, programManager, ticklerAccess, records);
    private MockedStatic<CarlosProperties> settings;
    private MockedStatic<EDocUtil> visibility;
    private MockedStatic<ClinicalSummaryTextExtractor> reader;
    private Document document;

    @BeforeEach void setUp() {
        injectDependency(context, "entityManager", em);
        settings = mockStatic(CarlosProperties.class);
        var properties = mock(CarlosProperties.class);
        settings.when(CarlosProperties::getInstance).thenReturn(properties);
        when(properties.getProperty(anyString(), eq("false"))).thenReturn("true");
        when(user.getLoggedInProviderNo()).thenReturn("101");
        when(user.getSession()).thenReturn(session);
        session.setAttribute("case_program_id", "10016");
        when(security.hasPrivilege(user, "_edoc", "r", null)).thenReturn(true);
        for (String permission : List.of("_edoc", "_demographic", "_eChart", "_tickler")) {
            when(security.hasPrivilege(user, permission, "r", 3001)).thenReturn(true);
        }
        when(security.isAllowedAccessToPatientRecord(user, 3001)).thenReturn(true);
        var demographic = new Demographic();
        demographic.setFirstName("Synthetic");
        demographic.setLastName("Patient");
        when(em.find(Demographic.class, 3001)).thenReturn(demographic);
        var link = new CtlDocument();
        link.setId(new CtlDocumentPK("demographic", 3001, 42));
        when(documents.getCtlDocumentByDocumentId(user, 42)).thenReturn(link);
        var membership = new ProgramProvider();
        membership.setRoleId(1L);
        when(programs.getProgramProvider("101", 10016L)).thenReturn(membership);
        when(programManager.hasAccessBasedOnCurrentFacility(user, 10016)).thenReturn(true);
        visibility = mockStatic(EDocUtil.class);
        EDoc visible = mock(EDoc.class);
        when(visible.getDocId()).thenReturn("42");
        visibility.when(() -> EDocUtil.listDocs(user, "demographic", "3001", "all", EDocUtil.PRIVATE,
                EDocUtil.EDocSort.OBSERVATIONDATE, "active")).thenReturn(new ArrayList<>(List.of(visible)));
        when(ticklerAccess.filterTicklersByAccess(anyList(), eq("101"), eq("10016")))
                .thenAnswer(call -> call.getArgument(0));
        document = new Document();
        document.setDocumentNo(42);
        document.setDocfilename("synthetic.txt");
        document.setContenttype("text/plain");
        document.setStatus('A');
        when(documents.getDocument(user, 42)).thenReturn(document);
        reader = mockStatic(ClinicalSummaryTextExtractor.class);
        reader.when(() -> ClinicalSummaryTextExtractor.document("synthetic.txt", "text/plain"))
                .thenReturn(new ClinicalSummaryTextExtractor.Extract("Review symptoms.", true, "Complete"));
    }
    @AfterEach void tearDown() { reader.close(); visibility.close(); settings.close(); }

    @Test void shouldReportEnabled_onlyWhenAllThreeFlagsAreOn() {
        assertThat(ChartUpdateContext.enabled()).isTrue();
        var properties = CarlosProperties.getInstance();
        // The three flags behind enabled(), which the eChart header, layout and document report call.
        for (String flag : List.of("clinical.ai_summary_generation.enabled", "clinical.ai_document_summary.enabled",
                "clinical.ai_chart_updates.enabled")) {
            when(properties.getProperty(flag, "false")).thenReturn("false");
            assertThat(ChartUpdateContext.enabled()).as(flag).isFalse();
            assertThatThrownBy(ChartUpdateContext::requireEnabled).isInstanceOf(IllegalStateException.class);
            when(properties.getProperty(flag, "false")).thenReturn("true");
        }
        assertThat(ChartUpdateContext.enabled()).isTrue();
    }

    @Test void shouldIdentifyMissingOriginal_withoutStoredHtmlFallback() throws Exception {
        reader.when(() -> ClinicalSummaryTextExtractor.document("synthetic.txt", "text/plain"))
                .thenThrow(new java.nio.file.NoSuchFileException("synthetic.txt"));
        assertThatThrownBy(() -> context.load(user, 42))
                .isInstanceOf(ChartUpdateContext.OriginalDocumentMissingException.class)
                .hasMessageContaining("original document file is missing");
        document.setDocxml("<p>Stored original</p>");
        assertThatThrownBy(() -> context.load(user, 42))
                .isExactlyInstanceOf(IllegalStateException.class).hasMessageContaining("Reopen the original");
    }

    @Test void shouldPreserveOriginalOption_whenExtractionFails() throws Exception {
        reader.when(() -> ClinicalSummaryTextExtractor.document("synthetic.txt", "text/plain"))
                .thenThrow(new java.io.IOException("Unreadable text"));
        assertThatThrownBy(() -> context.load(user, 42))
                .isExactlyInstanceOf(IllegalStateException.class).hasMessageContaining("Reopen the original");
    }

    @Test void shouldReportTextUnavailable_whenStoredFilenameIsRejected() throws Exception {
        reader.when(() -> ClinicalSummaryTextExtractor.document("synthetic.txt", "text/plain"))
                .thenThrow(new io.github.carlos_emr.carlos.utility.FileValidationException("Invalid filename"));
        assertThatThrownBy(() -> context.load(user, 42))
                .isExactlyInstanceOf(IllegalStateException.class).hasMessageContaining("Reopen the original");
    }

    @Test void shouldLoadSourceAndComparison_withoutGrantingWriteAccess() {
        var snapshot = context.load(user, 42);
        assertThat(snapshot.patientId()).isEqualTo(3001);
        assertThat(snapshot.source()).isEqualTo("Review symptoms.");
        verify(em).clear();
        assertThatThrownBy(() -> context.requireWrite(user, 3001, "tickler")).isInstanceOf(SecurityException.class);
    }

    @Test void shouldExcludeReminders_deniedByProgramRoleAccess() {
        var tickler = new Tickler();
        tickler.setId(7);
        tickler.setDemographicNo(3001);
        tickler.setProgramId(10016);
        tickler.setMessage("Restricted reminder");
        when(ticklers.findActiveByDemographicNo(3001)).thenReturn(List.of(tickler));
        when(ticklerAccess.filterTicklersByAccess(List.of(tickler), "101", "10016")).thenReturn(List.of());
        assertThat(context.load(user, 42).entries()).isEmpty();
    }

    @Test void shouldExcludeUnsignedNotes_beforeComparison() {
        var note = new io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote();
        note.setDemographic_no("3001");
        note.setNote("Unsigned private draft");
        note.setReporter_caisi_role("1");
        note.setSigned(false);
        when(notes.getNotes("3001")).thenReturn(List.of(note));
        context.load(user, 42);
        verify(notes).filterNotes(eq(user), eq("101"), argThat(java.util.Collection::isEmpty), eq("10016"));
    }

    @Test void shouldRequirePatientDocumentPermission_beforeReadingSource() {
        when(security.hasPrivilege(user, "_edoc", "r", 3001)).thenReturn(false);
        assertThatThrownBy(() -> context.load(user, 42)).isInstanceOf(SecurityException.class);
        reader.verifyNoInteractions();
        verifyNoInteractions(ticklers, notes);
    }

    @Test void shouldFormatObservationDate_forReminderDefaults() {
        document.setObservationdate(java.sql.Date.valueOf("2026-09-28"));
        assertThat(context.load(user, 42).date()).isEqualTo("2026-09-28");
        document.setObservationdate(new java.util.Date(java.sql.Date.valueOf("2026-09-28").getTime()));
        assertThat(context.load(user, 42).date()).isEqualTo("2026-09-28");
    }

    @Test void shouldRequirePatientPermissions_beforeReadingSource() {
        when(security.hasPrivilege(user, "_tickler", "r", 3001)).thenReturn(false);
        assertThatThrownBy(() -> context.load(user, 42)).isInstanceOf(SecurityException.class);
        reader.verifyNoInteractions();
        verifyNoInteractions(ticklers);
    }

    @Test void shouldRejectDocument_whenMislinkedOrUnavailable() {
        document.setDocumentNo(43);
        assertThatThrownBy(() -> context.load(user, 42)).isInstanceOf(SecurityException.class);
        document.setDocumentNo(42);
        document.setStatus('D');
        assertThatThrownBy(() -> context.load(user, 42)).isInstanceOf(SecurityException.class);
        reader.verifyNoInteractions();
    }

    @Test void shouldRequireProgramContext_andMembership() {
        session.removeAttribute("case_program_id");
        assertThatThrownBy(() -> context.load(user, 42)).hasMessageContaining("program context");
        session.setAttribute("case_program_id", "10016");
        when(programs.getProgramProvider("101", 10016L)).thenReturn(null);
        assertThatThrownBy(() -> context.load(user, 42)).isInstanceOf(SecurityException.class);
        reader.verifyNoInteractions();
    }

    @Test void shouldCheckSourceCompleteness_andDetectChanges() {
        String original = context.load(user, 42).fingerprint();
        reader.when(() -> ClinicalSummaryTextExtractor.document("synthetic.txt", "text/plain"))
                .thenReturn(new ClinicalSummaryTextExtractor.Extract("Changed source", true, "Complete"));
        assertThat(context.load(user, 42).fingerprint()).isNotEqualTo(original);
        reader.when(() -> ClinicalSummaryTextExtractor.document("synthetic.txt", "text/plain"))
                .thenReturn(new ClinicalSummaryTextExtractor.Extract("Truncated source", false, "Too long"));
        assertThatThrownBy(() -> context.load(user, 42)).hasMessageContaining("Complete readable");
    }
    @Test void shouldRequireSectionPrivileges_forEachNewDestination() {
        when(security.hasPrivilege(user, "_eChart", "w", 3001)).thenReturn(true);
        var section = new io.github.carlos_emr.carlos.model.security.Secobjprivilege();
        section.setObjectname_code("_SocHistory"); section.setPrivilege_code("r");
        when(security.getSecurityObjects(user)).thenReturn(List.of(section));
        assertThatThrownBy(() -> context.requireSectionWrite(user, 3001, "SocHistory")).isInstanceOf(SecurityException.class);
        section.setPrivilege_code("w");
        when(security.hasPrivilege(user, "_SocHistory", "w", 3001)).thenReturn(true);
        assertThatCode(() -> context.requireSectionWrite(user, 3001, "SocHistory")).doesNotThrowAnyException();
        when(security.hasPrivilege(user, "_FamHistory", "w", 3001)).thenReturn(true);
        assertThatThrownBy(() -> context.requireSectionWrite(user, 3001, "FamHistory")).isInstanceOf(SecurityException.class);
        when(security.hasPrivilege(user, "_newCasemgmt.familyHistory", "x", 3001)).thenReturn(true);
        assertThatCode(() -> context.requireSectionWrite(user, 3001, "FamHistory")).doesNotThrowAnyException();
        assertThatThrownBy(() -> context.requireSectionWrite(user, 3001, "Allergies")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void shouldDelegateConfiguredGlobalRights_toNativePermissionHierarchy() {
        var section = new io.github.carlos_emr.carlos.model.security.Secobjprivilege();
        section.setObjectname_code("_SocHistory"); section.setPrivilege_code("w");
        when(security.getSecurityObjects(user)).thenReturn(List.of(section));
        when(security.hasPrivilege(user, "_SocHistory", "r", 3001)).thenReturn(true);
        assertThat(ChartUpdateSections.accessible(security, user, 3001, "SocHistory", "r")).isTrue();
        // A priority restriction decided by the native resolver must defeat this raw grant.
        when(security.hasPrivilege(user, "_SocHistory", "r", 3001)).thenReturn(false);
        assertThat(ChartUpdateSections.accessible(security, user, 3001, "SocHistory", "r")).isFalse();
    }

    @Test void shouldUseStableDestinationOrder_forSerializedFingerprintInput() {
        var entry = new ChartUpdateContext.Entry("1", "history", "text", "text", "", "",
                new java.util.LinkedHashSet<>(List.of("SocHistory", "Concerns", "FamHistory")));
        assertThat(entry.destinations()).containsExactly("Concerns", "FamHistory", "SocHistory");
        assertThatThrownBy(() -> entry.destinations().add("MedHistory")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void shouldPreserveReadableSectionMembership_forComparisonAndFingerprint() {
        var note = new io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote();
        note.setId(7L); note.setDemographic_no("3001"); note.setSigned(true);
        note.setNote("Hypertension"); note.setReporter_caisi_role("1");
        var code = new io.github.carlos_emr.carlos.casemgmt.model.Issue();
        code.setCode("FamHistory");
        var issue = new io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue();
        issue.setIssue(code);
        note.setIssues(new java.util.HashSet<>(java.util.Set.of(issue)));
        when(notes.getNotes("3001")).thenReturn(List.of(note));
        when(notes.filterNotes(eq(user), eq("101"), anyList(), eq("10016"))).thenAnswer(call -> call.getArgument(2));
        when(security.hasPrivilege(user, "_newCasemgmt.familyHistory", "x", 3001)).thenReturn(true);
        var snapshot = context.load(user, 42);
        assertThat(snapshot.entries()).hasSize(1);
        assertThat(snapshot.entries().get(0).destinations()).containsExactly("FamHistory");
        code.setCode("SocHistory");
        assertThat(context.load(user, 42).fingerprint()).isNotEqualTo(snapshot.fingerprint());
        var restricted = new io.github.carlos_emr.carlos.model.security.Secobjprivilege();
        restricted.setObjectname_code("_SocHistory$3001"); restricted.setPrivilege_code("w");
        when(security.getSecurityObjects(user)).thenReturn(List.of(restricted));
        assertThat(context.load(user, 42).entries()).isEmpty();
    }

    @Test void shouldBuildOnlyAuthorizedPatientNativeLinks_withoutClinicalText() {
        assertThat(context.nativeReviewUrl(user, 3001, "Allergies")).isEmpty();
        when(security.hasPrivilege(user, "_allergy", "r", 3001)).thenReturn(true);
        when(security.hasPrivilege(user, "_allergy", "w", 3001)).thenReturn(true);
        assertThat(context.nativeReviewUrl(user, 3001, "Allergies")).isEmpty();
        when(security.hasPrivilege(user, "_prevention", "r", 3001)).thenReturn(true);
        when(security.hasPrivilege(user, "_prevention", "w", 3001)).thenReturn(true);
        assertThat(context.nativeReviewUrl(user, 3001, "Preventions")).isEqualTo("/prevention/ViewPreventionIndex?demographic_no=3001");
        assertThat(context.nativeReviewUrl(user, 3002, "Preventions")).isEmpty();
        when(security.hasPrivilege(user, "_demographic", "w", 3001)).thenReturn(true);
        assertThat(context.nativeReviewUrl(user, 3001, "Demographics")).isEqualTo("/demographic/DemographicEdit?demographic_no=3001");
        assertThat(context.nativeReviewUrl(user, 3001, "Measurements")).isEmpty();
        when(security.hasPrivilege(user, "_rx", "r", 3001)).thenReturn(true);
        when(security.hasPrivilege(user, "_rx", "w", 3001)).thenReturn(true);
        assertThat(context.nativeReviewUrl(user, 3001, "Medications")).isEmpty();
    }
}
