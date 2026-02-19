/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.enumerator.CppCode;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.NoteTo1;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for {@link NoteManager} business logic.
 *
 * <p>Tests cover all six public methods of NoteManager: getCppNotes, getActiveCppNotes
 * (two overloads), convertNote, isCppCode, and getIssueIds. Each method is tested
 * for normal operation, empty/null inputs, and edge cases.</p>
 *
 * <p>Model classes {@link CaseManagementNote} and {@link CaseManagementIssue} have field
 * initializers that call {@code SpringUtils.getBean()}, so their corresponding DAO mocks
 * must be registered before any test data is created.</p>
 *
 * @since 2026-02-09
 * @see NoteManager
 * @see OpenOUnitTestBase
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NoteManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("note")
public class NoteManagerUnitTest extends OpenOUnitTestBase {

    // NoteManager's @Autowired dependencies
    @Mock
    private CaseManagementManager mockCaseManagementManager;

    @Mock
    private CaseManagementNoteDAO mockCaseManagementNoteDAO;

    @Mock
    private IssueDAO mockIssueDAO;

    // Mocks required by model class field initializers (SpringUtils.getBean calls)
    @Mock
    private CaseManagementNoteLinkDAO mockCaseManagementNoteLinkDAO;

    @Mock
    private ProgramProviderDAO mockProgramProviderDAO;

    @Mock
    private ProgramAccessDAO mockProgramAccessDAO;

    @Mock
    private RoleProgramAccessDAO mockRoleProgramAccessDAO;

    // Session context mock
    private LoggedInInfo mockLoggedInInfo;

    private NoteManager noteManager;

    // Test constants
    private static final Integer TEST_DEMO_NO = 1001;
    private static final Long TEST_NOTE_ID = 100L;
    private static final String TEST_PROVIDER_NO = "999998";
    private static final String TEST_UUID = "test-uuid-1234";
    private static final String TEST_REVISION = "1";
    private static final String TEST_NOTE_TEXT = "Patient presents with symptoms.";
    private static final String TEST_ENCOUNTER_TYPE = "face to face encounter with client";
    private static final String TEST_ROLE_NAME = "doctor";
    private static final String TEST_PROGRAM_NAME = "OSCAR";

    /**
     * Initializes the test environment before each test method.
     *
     * <p>Registers mocks required by model class static initializers (CaseManagementNote
     * and CaseManagementIssue both call SpringUtils.getBean() during construction),
     * creates a fresh LoggedInInfo mock, instantiates the {@link NoteManager}, and
     * injects mock dependencies (CaseManagementManager, CaseManagementNoteDAO,
     * IssueDAO) via reflection.</p>
     */
    @BeforeEach
    void setUp() {
        // Register mocks for model class field initializers
        // CaseManagementNote calls SpringUtils.getBean(CaseManagementNoteLinkDAO.class)
        registerMock(CaseManagementNoteLinkDAO.class, mockCaseManagementNoteLinkDAO);
        // CaseManagementIssue calls SpringUtils.getBean for these three DAOs
        registerMock(ProgramProviderDAO.class, mockProgramProviderDAO);
        registerMock(ProgramAccessDAO.class, mockProgramAccessDAO);
        registerMock(RoleProgramAccessDAO.class, mockRoleProgramAccessDAO);

        // Create LoggedInInfo mock manually
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);

        // Create NoteManager instance and inject @Autowired dependencies
        noteManager = new NoteManager();
        injectDependency(noteManager, "caseManagementManager", mockCaseManagementManager);
        injectDependency(noteManager, "caseManagementNoteDAO", mockCaseManagementNoteDAO);
        injectDependency(noteManager, "issueDAO", mockIssueDAO);
    }

    // -------------------------------------------------------------------------
    // Test data builders
    // -------------------------------------------------------------------------

    /**
     * Creates a CaseManagementNote with sensible defaults for testing.
     */
    private CaseManagementNote createTestNote(Long id) {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(id);
        note.setSigned(true);
        note.setRevision(TEST_REVISION);
        note.setObservation_date(new Date());
        note.setUpdate_date(new Date());
        note.setProviderNo(TEST_PROVIDER_NO);
        note.setEncounter_type(TEST_ENCOUNTER_TYPE);
        note.setRoleName(TEST_ROLE_NAME);
        note.setProgramName(TEST_PROGRAM_NAME);
        note.setUuid(TEST_UUID);
        note.setLocked(false);
        note.setNote(TEST_NOTE_TEXT);
        note.setPosition(1);
        note.setAppointmentNo(500);
        note.setDemographic_no(String.valueOf(TEST_DEMO_NO));
        return note;
    }

    /**
     * Creates an Issue with the given id and code.
     */
    private Issue createTestIssue(Long id, String code) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setCode(code);
        issue.setDescription("Description for " + code);
        issue.setRole(TEST_ROLE_NAME);
        issue.setType(Issue.SYSTEM);
        return issue;
    }

    /**
     * Creates a CaseManagementIssue linked to an Issue with the given code.
     */
    private CaseManagementIssue createTestCaseManagementIssue(Long id, String issueCode) {
        CaseManagementIssue cmIssue = new CaseManagementIssue();
        cmIssue.setId(id);
        cmIssue.setDemographic_no(TEST_DEMO_NO);
        cmIssue.setIssue(createTestIssue(id, issueCode));
        cmIssue.setIssue_id(id);
        cmIssue.setAcute(false);
        cmIssue.setCertain(true);
        cmIssue.setMajor(false);
        cmIssue.setResolved(false);
        cmIssue.setType("doctor");
        return cmIssue;
    }

    /**
     * Creates a CaseManagementNoteExt with the given key and string value.
     */
    private CaseManagementNoteExt createTestNoteExt(Long noteId, String key, String value) {
        CaseManagementNoteExt ext = new CaseManagementNoteExt();
        ext.setId(noteId + 1000L);
        ext.setNoteId(noteId);
        ext.setKeyVal(key);
        ext.setValue(value);
        return ext;
    }

    // =========================================================================
    // getCppNotes tests
    // =========================================================================

    /** Tests for {@link NoteManager#getCppNotes(LoggedInInfo, Integer)}. */
    @Nested
    @DisplayName("getCppNotes")
    @Tag("read")
    class GetCppNotesTests {

        @Test
        @DisplayName("should return empty list when no CPP notes exist for demographic")
        void shouldReturnEmptyList_whenNoCppNotesExist() {
            // Given
            when(mockCaseManagementNoteDAO.findNotesByDemographicAndIssueCode(
                    eq(TEST_DEMO_NO), any(String[].class)))
                    .thenReturn(Collections.emptyList());

            // When
            List<NoteTo1> result = noteManager.getCppNotes(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("should pass all CPP codes to DAO query")
        void shouldPassAllCppCodes_whenQuerying() {
            // Given
            when(mockCaseManagementNoteDAO.findNotesByDemographicAndIssueCode(
                    anyInt(), any(String[].class)))
                    .thenReturn(Collections.emptyList());

            // When
            noteManager.getCppNotes(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            verify(mockCaseManagementNoteDAO).findNotesByDemographicAndIssueCode(
                    eq(TEST_DEMO_NO), eq(CppCode.toArray()));
        }

        @Test
        @DisplayName("should convert each note and return transfer objects")
        void shouldConvertAndReturnNotes_whenNotesExist() {
            // Given
            CaseManagementNote note1 = createTestNote(1L);
            CaseManagementNote note2 = createTestNote(2L);
            when(mockCaseManagementNoteDAO.findNotesByDemographicAndIssueCode(
                    eq(TEST_DEMO_NO), any(String[].class)))
                    .thenReturn(Arrays.asList(note1, note2));
            when(mockCaseManagementManager.getExtByNote(anyLong()))
                    .thenReturn(Collections.emptyList());

            // When
            List<NoteTo1> result = noteManager.getCppNotes(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getNoteId()).isEqualTo(1);
            assertThat(result.get(1).getNoteId()).isEqualTo(2);
        }

        @Test
        @DisplayName("should call getExtByNote for each returned note")
        void shouldCallGetExtByNote_forEachNote() {
            // Given
            CaseManagementNote note1 = createTestNote(10L);
            CaseManagementNote note2 = createTestNote(20L);
            when(mockCaseManagementNoteDAO.findNotesByDemographicAndIssueCode(
                    anyInt(), any(String[].class)))
                    .thenReturn(Arrays.asList(note1, note2));
            when(mockCaseManagementManager.getExtByNote(anyLong()))
                    .thenReturn(Collections.emptyList());

            // When
            noteManager.getCppNotes(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            verify(mockCaseManagementManager).getExtByNote(10L);
            verify(mockCaseManagementManager).getExtByNote(20L);
        }
    }

    // =========================================================================
    // getActiveCppNotes tests
    // =========================================================================

    /** Tests for {@link NoteManager#getActiveCppNotes(LoggedInInfo, Integer)} and
     *  {@link NoteManager#getActiveCppNotes(LoggedInInfo, Integer, String[])}. */
    @Nested
    @DisplayName("getActiveCppNotes")
    @Tag("read")
    class GetActiveCppNotesTests {

        @Test
        @DisplayName("should return empty list when no active CPP notes exist")
        void shouldReturnEmptyList_whenNoActiveNotesExist() {
            // Given
            when(mockIssueDAO.findIssueByCode(any(String[].class)))
                    .thenReturn(Collections.emptyList());
            when(mockCaseManagementNoteDAO.getActiveNotesByDemographic(
                    anyString(), any(String[].class)))
                    .thenReturn(Collections.emptyList());

            // When
            List<NoteTo1> result = noteManager.getActiveCppNotes(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("should use default CPP codes when no custom codes provided")
        void shouldUseDefaultCppCodes_whenNoCustomCodesProvided() {
            // Given
            when(mockIssueDAO.findIssueByCode(any(String[].class)))
                    .thenReturn(Collections.emptyList());
            when(mockCaseManagementNoteDAO.getActiveNotesByDemographic(
                    anyString(), any(String[].class)))
                    .thenReturn(Collections.emptyList());

            // When
            noteManager.getActiveCppNotes(mockLoggedInInfo, TEST_DEMO_NO);

            // Then - should call issueDAO with default CppCode array
            verify(mockIssueDAO).findIssueByCode(eq(CppCode.toArray()));
        }

        @Test
        @DisplayName("should use custom CPP codes when provided")
        void shouldUseCustomCppCodes_whenProvided() {
            // Given
            String[] customCodes = {"OMeds", "Concerns"};
            when(mockIssueDAO.findIssueByCode(any(String[].class)))
                    .thenReturn(Collections.emptyList());
            when(mockCaseManagementNoteDAO.getActiveNotesByDemographic(
                    anyString(), any(String[].class)))
                    .thenReturn(Collections.emptyList());

            // When
            noteManager.getActiveCppNotes(mockLoggedInInfo, TEST_DEMO_NO, customCodes);

            // Then
            verify(mockIssueDAO).findIssueByCode(eq(customCodes));
        }

        @Test
        @DisplayName("should pass demographic number as string to DAO")
        void shouldPassDemographicAsString_whenQuerying() {
            // Given
            when(mockIssueDAO.findIssueByCode(any(String[].class)))
                    .thenReturn(Collections.emptyList());
            when(mockCaseManagementNoteDAO.getActiveNotesByDemographic(
                    anyString(), any(String[].class)))
                    .thenReturn(Collections.emptyList());

            // When
            noteManager.getActiveCppNotes(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            verify(mockCaseManagementNoteDAO).getActiveNotesByDemographic(
                    eq(String.valueOf(TEST_DEMO_NO)), any(String[].class));
        }

        @Test
        @DisplayName("should pass resolved issue IDs to the note DAO")
        void shouldPassResolvedIssueIds_toNoteDao() {
            // Given
            Issue issue1 = createTestIssue(10L, "OMeds");
            Issue issue2 = createTestIssue(20L, "Concerns");
            when(mockIssueDAO.findIssueByCode(any(String[].class)))
                    .thenReturn(Arrays.asList(issue1, issue2));
            when(mockCaseManagementNoteDAO.getActiveNotesByDemographic(
                    anyString(), any(String[].class)))
                    .thenReturn(Collections.emptyList());

            // When
            noteManager.getActiveCppNotes(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            verify(mockCaseManagementNoteDAO).getActiveNotesByDemographic(
                    eq(String.valueOf(TEST_DEMO_NO)), eq(new String[]{"10", "20"}));
        }

        @Test
        @DisplayName("should convert each active note to transfer object")
        void shouldConvertEachActiveNote_whenNotesExist() {
            // Given
            when(mockIssueDAO.findIssueByCode(any(String[].class)))
                    .thenReturn(Collections.emptyList());

            CaseManagementNote note = createTestNote(TEST_NOTE_ID);
            when(mockCaseManagementNoteDAO.getActiveNotesByDemographic(
                    anyString(), any(String[].class)))
                    .thenReturn(Collections.singletonList(note));
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            List<NoteTo1> result = noteManager.getActiveCppNotes(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getNoteId()).isEqualTo(TEST_NOTE_ID.intValue());
        }
    }

    // =========================================================================
    // convertNote tests
    // =========================================================================

    /** Tests for {@link NoteManager#convertNote(LoggedInInfo, CaseManagementNote)}. */
    @Nested
    @DisplayName("convertNote")
    @Tag("read")
    class ConvertNoteTests {

        @Test
        @DisplayName("should map core note fields correctly")
        void shouldMapCoreFields_whenConverting() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getNoteId()).isEqualTo(TEST_NOTE_ID.intValue());
            assertThat(result.getIsSigned()).isTrue();
            assertThat(result.getRevision()).isEqualTo(TEST_REVISION);
            assertThat(result.getObservationDate()).isNotNull();
            assertThat(result.getUpdateDate()).isNotNull();
            assertThat(result.getProviderNo()).isEqualTo(TEST_PROVIDER_NO);
            assertThat(result.getStatus()).isEqualTo("Signed");
            assertThat(result.getProgramName()).isEqualTo(TEST_PROGRAM_NAME);
            assertThat(result.getRoleName()).isEqualTo(TEST_ROLE_NAME);
            assertThat(result.getUuid()).isEqualTo(TEST_UUID);
            assertThat(result.getLocked()).isFalse();
            assertThat(result.getNote()).isEqualTo(TEST_NOTE_TEXT);
            assertThat(result.getEncounterType()).isEqualTo(TEST_ENCOUNTER_TYPE);
            assertThat(result.getPosition()).isEqualTo(1);
            assertThat(result.getAppointmentNo()).isEqualTo(500);
        }

        @Test
        @DisplayName("should set provider name to DELETED when provider is null")
        void shouldSetProviderNameDeleted_whenProviderIsNull() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            // provider is null by default
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getProviderName()).isEqualTo("DELETED");
        }

        @Test
        @DisplayName("should default cpp flag to false when no issues present")
        void shouldSetCppFalse_whenNoIssuesPresent() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.isCpp()).isFalse();
        }

        @Test
        @DisplayName("should set cpp flag to true when note has CPP issue")
        void shouldSetCppTrue_whenNoteHasCppIssue() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            CaseManagementIssue cppIssue = createTestCaseManagementIssue(1L, CppCode.OMEDS.getCode());
            cmNote.setIssues(new HashSet<>(Collections.singletonList(cppIssue)));
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.isCpp()).isTrue();
        }

        @Test
        @DisplayName("should keep cpp flag false when note has only non-CPP issues")
        void shouldKeepCppFalse_whenOnlyNonCppIssues() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            CaseManagementIssue nonCppIssue = createTestCaseManagementIssue(1L, "SomeNonCppCode");
            cmNote.setIssues(new HashSet<>(Collections.singletonList(nonCppIssue)));
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.isCpp()).isFalse();
        }

        @Test
        @DisplayName("should build summary code from all issue codes")
        void shouldBuildSummaryCode_fromAllIssueCodes() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            CaseManagementIssue issue1 = createTestCaseManagementIssue(1L, "OMeds");
            CaseManagementIssue issue2 = createTestCaseManagementIssue(2L, "Concerns");
            // Use a LinkedHashSet to maintain insertion order for assertion
            Set<CaseManagementIssue> issueSet = new LinkedHashSet<>();
            issueSet.add(issue1);
            issueSet.add(issue2);
            cmNote.setIssues(issueSet);
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getSummaryCode()).contains("OMeds");
            assertThat(result.getSummaryCode()).contains("Concerns");
        }

        @Test
        @DisplayName("should return empty summary code when no issues")
        void shouldReturnEmptySummaryCode_whenNoIssues() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getSummaryCode()).isEmpty();
        }

        @Test
        @DisplayName("should set noteExt with empty values when no extensions exist")
        void shouldSetNoteExtEmpty_whenNoExtensions() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getNoteExt()).isNotNull();
            assertThat(result.getNoteExt().getNoteId()).isEqualTo(TEST_NOTE_ID);
            assertThat(result.getNoteExt().getTreatment()).isNull();
            assertThat(result.getNoteExt().getAgeAtOnset()).isNull();
        }

        @Test
        @DisplayName("should map treatment extension to noteExt")
        void shouldMapTreatmentExtension() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            CaseManagementNoteExt ext = createTestNoteExt(TEST_NOTE_ID,
                    CaseManagementNoteExt.TREATMENT, "Prescribed medication");
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.singletonList(ext));

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getNoteExt().getTreatment()).isEqualTo("Prescribed medication");
        }

        @Test
        @DisplayName("should map age at onset extension to noteExt")
        void shouldMapAgeAtOnsetExtension() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            CaseManagementNoteExt ext = createTestNoteExt(TEST_NOTE_ID,
                    CaseManagementNoteExt.AGEATONSET, "45");
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.singletonList(ext));

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getNoteExt().getAgeAtOnset()).isEqualTo("45");
        }

        @Test
        @DisplayName("should map problem status extension to noteExt")
        void shouldMapProblemStatusExtension() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            CaseManagementNoteExt ext = createTestNoteExt(TEST_NOTE_ID,
                    CaseManagementNoteExt.PROBLEMSTATUS, "Active");
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.singletonList(ext));

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getNoteExt().getProblemStatus()).isEqualTo("Active");
        }

        @Test
        @DisplayName("should map exposure detail extension to noteExt")
        void shouldMapExposureDetailExtension() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            CaseManagementNoteExt ext = createTestNoteExt(TEST_NOTE_ID,
                    CaseManagementNoteExt.EXPOSUREDETAIL, "Workplace chemical");
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.singletonList(ext));

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getNoteExt().getExposureDetail()).isEqualTo("Workplace chemical");
        }

        @Test
        @DisplayName("should map relationship extension to noteExt")
        void shouldMapRelationshipExtension() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            CaseManagementNoteExt ext = createTestNoteExt(TEST_NOTE_ID,
                    CaseManagementNoteExt.RELATIONSHIP, "Parent");
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.singletonList(ext));

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getNoteExt().getRelationship()).isEqualTo("Parent");
        }

        @Test
        @DisplayName("should map life stage extension to noteExt")
        void shouldMapLifeStageExtension() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            CaseManagementNoteExt ext = createTestNoteExt(TEST_NOTE_ID,
                    CaseManagementNoteExt.LIFESTAGE, "Adult");
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.singletonList(ext));

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getNoteExt().getLifeStage()).isEqualTo("Adult");
        }

        @Test
        @DisplayName("should map hide CPP extension to noteExt")
        void shouldMapHideCppExtension() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            CaseManagementNoteExt ext = createTestNoteExt(TEST_NOTE_ID,
                    CaseManagementNoteExt.HIDECPP, "true");
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.singletonList(ext));

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getNoteExt().getHideCpp()).isEqualTo("true");
        }

        @Test
        @DisplayName("should map problem description extension to noteExt")
        void shouldMapProblemDescExtension() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            CaseManagementNoteExt ext = createTestNoteExt(TEST_NOTE_ID,
                    CaseManagementNoteExt.PROBLEMDESC, "Chronic back pain");
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.singletonList(ext));

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getNoteExt().getProblemDesc()).isEqualTo("Chronic back pain");
        }

        @Test
        @DisplayName("should map multiple extensions in single conversion")
        void shouldMapMultipleExtensions_whenPresent() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            List<CaseManagementNoteExt> exts = Arrays.asList(
                    createTestNoteExt(TEST_NOTE_ID, CaseManagementNoteExt.TREATMENT, "Surgery"),
                    createTestNoteExt(TEST_NOTE_ID, CaseManagementNoteExt.AGEATONSET, "60"),
                    createTestNoteExt(TEST_NOTE_ID, CaseManagementNoteExt.LIFESTAGE, "Senior")
            );
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(exts);

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getNoteExt().getTreatment()).isEqualTo("Surgery");
            assertThat(result.getNoteExt().getAgeAtOnset()).isEqualTo("60");
            assertThat(result.getNoteExt().getLifeStage()).isEqualTo("Senior");
        }

        @Test
        @DisplayName("should populate assigned issues from converter")
        void shouldPopulateAssignedIssues_whenIssuesPresent() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(5L, "OMeds");
            cmNote.setIssues(new HashSet<>(Collections.singletonList(cmIssue)));
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getAssignedIssues()).isNotNull();
            assertThat(result.getAssignedIssues()).hasSize(1);
        }

        @Test
        @DisplayName("should set hasHistory based on note history content")
        void shouldSetHasHistory_basedOnNoteContent() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            cmNote.setHistory("Previous data\n----------------History Record----------------\nOlder data");
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getHasHistory()).isTrue();
        }

        @Test
        @DisplayName("should set hasHistory to false when no history marker")
        void shouldSetHasHistoryFalse_whenNoHistoryMarker() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            // No history set, so getHasHistory() returns false
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getHasHistory()).isFalse();
        }

        @Test
        @DisplayName("should handle unsigned note status correctly")
        void shouldHandleUnsignedStatus() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            cmNote.setSigned(false);
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.getIsSigned()).isFalse();
            assertThat(result.getStatus()).isEqualTo("Unsigned");
        }

        @Test
        @DisplayName("should set rxAnnotation to false when no drug link exists")
        void shouldSetRxAnnotationFalse_whenNoDrugLink() {
            // Given
            CaseManagementNote cmNote = createTestNote(TEST_NOTE_ID);
            when(mockCaseManagementManager.getExtByNote(TEST_NOTE_ID))
                    .thenReturn(Collections.emptyList());

            // When
            NoteTo1 result = noteManager.convertNote(mockLoggedInInfo, cmNote);

            // Then
            assertThat(result.isRxAnnotation()).isFalse();
        }
    }

    // =========================================================================
    // isCppCode tests
    // =========================================================================

    /** Tests for {@link NoteManager#isCppCode(CaseManagementIssue)}. */
    @Nested
    @DisplayName("isCppCode")
    @Tag("read")
    class IsCppCodeTests {

        @Test
        @DisplayName("should return true for OMeds CPP code")
        void shouldReturnTrue_forOMedsCppCode() {
            // Given
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(1L, CppCode.OMEDS.getCode());

            // When
            boolean result = noteManager.isCppCode(cmIssue);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for SocHistory CPP code")
        void shouldReturnTrue_forSocHistoryCppCode() {
            // Given
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(2L, CppCode.SOC_HISTORY.getCode());

            // When
            boolean result = noteManager.isCppCode(cmIssue);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for MedHistory CPP code")
        void shouldReturnTrue_forMedHistoryCppCode() {
            // Given
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(3L, CppCode.MED_HISTORY.getCode());

            // When
            boolean result = noteManager.isCppCode(cmIssue);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for Concerns CPP code")
        void shouldReturnTrue_forConcernsCppCode() {
            // Given
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(4L, CppCode.CONCERNS.getCode());

            // When
            boolean result = noteManager.isCppCode(cmIssue);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for FamHistory CPP code")
        void shouldReturnTrue_forFamHistoryCppCode() {
            // Given
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(5L, CppCode.FAM_HISTORY.getCode());

            // When
            boolean result = noteManager.isCppCode(cmIssue);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for Reminders CPP code")
        void shouldReturnTrue_forRemindersCppCode() {
            // Given
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(6L, CppCode.REMINDERS.getCode());

            // When
            boolean result = noteManager.isCppCode(cmIssue);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for RiskFactors CPP code")
        void shouldReturnTrue_forRiskFactorsCppCode() {
            // Given
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(7L, CppCode.RISK_FACTORS.getCode());

            // When
            boolean result = noteManager.isCppCode(cmIssue);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for OcularMedication CPP code")
        void shouldReturnTrue_forOcularMedicationCppCode() {
            // Given
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(8L, CppCode.OCULAR_MEDICATION.getCode());

            // When
            boolean result = noteManager.isCppCode(cmIssue);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for TicklerNote CPP code")
        void shouldReturnTrue_forTicklerNoteCppCode() {
            // Given
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(9L, CppCode.TICKLER_NOTE.getCode());

            // When
            boolean result = noteManager.isCppCode(cmIssue);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for non-CPP code")
        void shouldReturnFalse_forNonCppCode() {
            // Given
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(10L, "ICD9-250");

            // When
            boolean result = noteManager.isCppCode(cmIssue);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for empty code string")
        void shouldReturnFalse_forEmptyCodeString() {
            // Given
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(11L, "");

            // When
            boolean result = noteManager.isCppCode(cmIssue);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for case-mismatched CPP code")
        void shouldReturnFalse_forCaseMismatchedCode() {
            // Given - CppCode is case-sensitive ("OMeds" not "omeds")
            CaseManagementIssue cmIssue = createTestCaseManagementIssue(12L, "omeds");

            // When
            boolean result = noteManager.isCppCode(cmIssue);

            // Then
            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // getIssueIds tests
    // =========================================================================

    /** Tests for {@link NoteManager#getIssueIds(String[])}. */
    @Nested
    @DisplayName("getIssueIds")
    @Tag("read")
    class GetIssueIdsTests {

        @Test
        @DisplayName("should use default CPP codes when null is passed")
        void shouldUseDefaultCppCodes_whenNullPassed() {
            // Given
            when(mockIssueDAO.findIssueByCode(any(String[].class)))
                    .thenReturn(Collections.emptyList());

            // When
            noteManager.getIssueIds(null);

            // Then
            verify(mockIssueDAO).findIssueByCode(eq(CppCode.toArray()));
        }

        @Test
        @DisplayName("should use default CPP codes when empty array is passed")
        void shouldUseDefaultCppCodes_whenEmptyArrayPassed() {
            // Given
            when(mockIssueDAO.findIssueByCode(any(String[].class)))
                    .thenReturn(Collections.emptyList());

            // When
            noteManager.getIssueIds(new String[]{});

            // Then
            verify(mockIssueDAO).findIssueByCode(eq(CppCode.toArray()));
        }

        @Test
        @DisplayName("should use custom codes when non-empty array is passed")
        void shouldUseCustomCodes_whenNonEmptyArrayPassed() {
            // Given
            String[] customCodes = {"OMeds", "FamHistory"};
            when(mockIssueDAO.findIssueByCode(customCodes))
                    .thenReturn(Collections.emptyList());

            // When
            noteManager.getIssueIds(customCodes);

            // Then
            verify(mockIssueDAO).findIssueByCode(eq(customCodes));
        }

        @Test
        @DisplayName("should return empty array when no issues found")
        void shouldReturnEmptyArray_whenNoIssuesFound() {
            // Given
            when(mockIssueDAO.findIssueByCode(any(String[].class)))
                    .thenReturn(Collections.emptyList());

            // When
            String[] result = noteManager.getIssueIds(null);

            // Then
            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("should return issue IDs as string array")
        void shouldReturnIssueIdsAsStringArray_whenIssuesFound() {
            // Given
            Issue issue1 = createTestIssue(10L, "OMeds");
            Issue issue2 = createTestIssue(20L, "Concerns");
            Issue issue3 = createTestIssue(30L, "MedHistory");
            when(mockIssueDAO.findIssueByCode(any(String[].class)))
                    .thenReturn(Arrays.asList(issue1, issue2, issue3));

            // When
            String[] result = noteManager.getIssueIds(null);

            // Then
            assertThat(result).containsExactly("10", "20", "30");
        }

        @Test
        @DisplayName("should return single ID when only one issue matches")
        void shouldReturnSingleId_whenOneIssueMatches() {
            // Given
            Issue issue = createTestIssue(42L, "Reminders");
            when(mockIssueDAO.findIssueByCode(any(String[].class)))
                    .thenReturn(Collections.singletonList(issue));

            // When
            String[] result = noteManager.getIssueIds(null);

            // Then
            assertThat(result).containsExactly("42");
        }

        @Test
        @DisplayName("should convert issue IDs to strings correctly")
        void shouldConvertIssueIdsToStrings() {
            // Given
            Issue issue = createTestIssue(999L, "RiskFactors");
            when(mockIssueDAO.findIssueByCode(any(String[].class)))
                    .thenReturn(Collections.singletonList(issue));

            // When
            String[] result = noteManager.getIssueIds(new String[]{"RiskFactors"});

            // Then
            assertThat(result).hasSize(1);
            assertThat(result[0]).isEqualTo("999");
        }
    }
}
