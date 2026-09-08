/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.RxStatus;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiClinicalSummaryPrototypeChartUnitTest extends CarlosUnitTestBase {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final DemographicManager demographics = mock(DemographicManager.class);
    private final CaseManagementManager notes = mock(CaseManagementManager.class);
    private final RxManager medications = mock(RxManager.class);
    private final AllergyManager allergies = mock(AllergyManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final HttpSession session = mock(HttpSession.class);
    private final CarlosProperties properties = mock(CarlosProperties.class);
    private MockedStatic<CarlosProperties> settings;
    private ChartClinicalSummaryProvider provider;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(DemographicManager.class, demographics);
        registerMock(CaseManagementManager.class, notes);
        registerMock(RxManager.class, medications);
        registerMock(AllergyManager.class, allergies);
        settings = mockStatic(CarlosProperties.class);
        settings.when(CarlosProperties::getInstance).thenReturn(properties);
        when(user.getSession()).thenReturn(session);
        when(user.getLoggedInProviderNo()).thenReturn("test-provider");
        when(notes.isClientInProgramDomain("test-provider", "42")).thenReturn(true);
        when(security.hasPrivilege(user, "_eChart", "r", 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_demographic", "r", 42)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 42)).thenReturn(true);
        Demographic patient = mock(Demographic.class);
        when(patient.getDemographicNo()).thenReturn(42);
        when(patient.getDisplayName()).thenReturn("Example, Patient");
        when(demographics.getDemographic(user, Integer.valueOf(42))).thenReturn(patient);
        provider = new ChartClinicalSummaryProvider();
    }

    @AfterEach
    void tearDown() {
        settings.close();
    }

    private ClinicalSummaryArtifact load() {
        return provider.load(user, ClinicalSummaryRequest.chart(42));
    }

    @Test
    void patientDenyStopsAllClinicalReads() {
        when(security.isAllowedAccessToPatientRecord(user, 42)).thenReturn(false);
        assertThatThrownBy(this::load).isInstanceOf(SecurityException.class);
        verifyNoInteractions(demographics, medications, allergies, notes);
        logActionMock.verifyNoInteractions();
    }

    @Test
    void chartPrivilegeIsPatientSpecific() {
        when(security.hasPrivilege(user, "_eChart", "r", 42)).thenReturn(false);
        assertThatThrownBy(this::load).isInstanceOf(SecurityException.class);
        verifyNoInteractions(demographics, medications, allergies, notes);
    }

    @Test
    void demographicPrivilegeIsRequired() {
        when(security.hasPrivilege(user, "_demographic", "r", 42)).thenReturn(false);
        assertThatThrownBy(this::load).isInstanceOf(SecurityException.class);
        verifyNoInteractions(demographics, medications, allergies, notes);
    }

    @Test
    void programDomainDenialPrecedesRetrieval() {
        when(notes.isClientInProgramDomain("test-provider", "42")).thenReturn(false);
        assertThatThrownBy(this::load).isInstanceOf(SecurityException.class);
        verifyNoInteractions(demographics, medications, allergies);
        verify(notes, never()).getNotes(anyString(), anyInt());
    }

    @Test
    void missingPatientDoesNotProduceAnArtifact() {
        when(demographics.getDemographic(user, Integer.valueOf(42))).thenReturn(null);
        assertThatThrownBy(this::load).isInstanceOf(java.util.NoSuchElementException.class);
        verifyNoInteractions(medications, allergies);
    }

    @Test
    void restrictedModulesAreNotReadAndMissingContextDoesNotReadNotes() {
        ClinicalSummaryArtifact artifact = load();
        assertThat(artifact.isRenderable()).isTrue();
        assertThat(artifact.getClaimsById()).isEmpty();
        assertThat(artifact.getView().get("patient_context")).isEqualTo(Map.of(
                "id", "demographic-42", "label", "Example, Patient", "synthetic", false));
        assertThat(artifact.getView().get("validation").toString())
                .contains("medications_unavailable", "allergies_unavailable", "notes_unavailable");
        verifyNoInteractions(medications, allergies);
        verify(notes, never()).getNotes(anyString(), anyInt());
        logActionMock.verify(() -> LogAction.addLogSynchronous(user, "ClinicalSummary.read", "demographicNo=42"));
    }

    @Test
    void reproducesRecordedFieldsWithCitationsWithoutInferringCurrentUse() {
        when(security.hasPrivilege(user, "_rx", "r", 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_allergy", "r", 42)).thenReturn(true);
        Drug drug = mock(Drug.class);
        when(drug.getId()).thenReturn(7);
        when(drug.getDemographicId()).thenReturn(42);
        when(drug.getDrugName()).thenReturn("Example medication");
        when(drug.getSpecial()).thenReturn("Recorded instructions\n<script>example</script>");
        when(drug.isDiscontinued()).thenReturn(true);
        when(medications.getDrugs(user, 42, RxStatus.CURRENT)).thenReturn(List.of(drug));
        Allergy allergy = mock(Allergy.class);
        when(allergy.getId()).thenReturn(8);
        when(allergy.getDemographicNo()).thenReturn(42);
        when(allergy.getDescription()).thenReturn("Example allergen");
        when(allergy.getReaction()).thenReturn("Recorded rash");
        when(allergies.getActiveAllergies(user, 42)).thenReturn(List.of(allergy));
        ClinicalSummaryArtifact artifact = load();
        assertThat(artifact.getClaimsById()).containsOnlyKeys("claim-rx-7", "claim-allergy-8");
        assertThat(artifact.getClaimsById().get("claim-rx-7").get("text").toString())
                .contains("Discontinued flag: true", "Recorded instructions\n<script>example</script>");
        assertThat(artifact.getClaimsById().get("claim-allergy-8").get("source_ids")).isEqualTo(List.of("allergy-8"));
    }

    @Test
    void rejectsCrossPatientSourceBeforeRendering() {
        when(security.hasPrivilege(user, "_rx", "r", 42)).thenReturn(true);
        Drug foreign = mock(Drug.class);
        when(foreign.getDemographicId()).thenReturn(43);
        when(medications.getDrugs(user, 42, RxStatus.CURRENT)).thenReturn(List.of(foreign));
        assertThatThrownBy(this::load).isInstanceOf(SecurityException.class).hasMessage("Chart source patient mismatch");
    }

    @Test
    void notesMustPassExistingProgramAndFacilityFilterAndKeepFullSource() {
        when(session.getAttribute("case_program_id")).thenReturn("12");
        CaseManagementNote allowed = note(1L);
        CaseManagementNote denied = note(2L);
        CaseManagementNote unsigned = note(3L);
        when(unsigned.isSigned()).thenReturn(false);
        CaseManagementNote locked = note(4L);
        when(locked.isLocked()).thenReturn(true);
        CaseManagementNote archived = note(5L);
        when(archived.isArchived()).thenReturn(true);
        when(notes.getNotes("42", 51)).thenReturn(List.of(allowed, denied, unsigned, locked, archived));
        when(notes.filterNotes(user, "test-provider", List.of(allowed, denied), "12")).thenReturn(List.of(allowed));
        ClinicalSummaryArtifact artifact = load();
        verify(notes).filterNotes(user, "test-provider", List.of(allowed, denied), "12");
        assertThat(artifact.getClaimsById()).containsOnlyKeys("claim-note-1");
        assertThat(artifact.getClaimsById().get("claim-note-1").get("text").toString()).endsWith("... [excerpt]");
        assertThat(artifact.getView().get("sources").toString()).contains("Full note ".repeat(40));
        assertThat(artifact.getView().get("sources").toString()).doesNotContain("note-2", "note-3", "note-4", "note-5");
    }

    @Test
    void separatePatientRequestsDoNotReusePriorChart() {
        load();
        assertThatThrownBy(() -> provider.load(user, ClinicalSummaryRequest.chart(43)))
                .isInstanceOf(SecurityException.class);
        verify(demographics, never()).getDemographic(user, Integer.valueOf(43));
    }

    private CaseManagementNote note(long id) {
        CaseManagementNote note = mock(CaseManagementNote.class);
        when(note.getId()).thenReturn(id);
        when(note.getDemographic_no()).thenReturn("42");
        when(note.isSigned()).thenReturn(true);
        when(note.getReporter_caisi_role()).thenReturn("1");
        when(note.getNote()).thenReturn("Full note ".repeat(40));
        return note;
    }
}
