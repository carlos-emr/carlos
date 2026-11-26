/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.forms.RehabStudyFormDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.model.rehab.FormIntakeInfo;
import io.github.carlos_emr.carlos.form.model.rehab.FormSF36;
import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyAssessment;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Access checks, new-form prefill and trusted identifiers of the rehabilitation-study service.
 *
 * @since 2026-11-26
 */
@Tag("unit")
@Tag("form")
@DisplayName("Rehabilitation study form service")
class RehabStudyFormServiceUnitTest {
    private static final int PATIENT = 123;
    private RehabStudyFormDao forms;
    private DemographicManager demographics;
    private SecurityInfoManager security;
    private LoggedInInfo actor;
    private RehabStudyFormService service;

    @BeforeEach
    void prepare() {
        forms = mock(RehabStudyFormDao.class);
        demographics = mock(DemographicManager.class);
        security = mock(SecurityInfoManager.class);
        actor = mock(LoggedInInfo.class);
        service = new RehabStudyFormService(forms, demographics, security);
        when(security.hasPrivilege(any(), anyString(), anyString(), anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(actor, PATIENT)).thenReturn(true);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
        when(forms.findStudyId(anyInt())).thenReturn(Optional.empty());
    }

    private static RehabStudyFormType<?> type(String formClass) {
        return RehabStudyForms.forFormClass(formClass).orElseThrow();
    }

    private static Demographic patient() {
        Demographic patient = new Demographic();
        patient.setDemographicNo(PATIENT);
        patient.setSex(" F ");
        patient.setYearOfBirth(" 1950 ");
        patient.setMonthOfBirth("02");
        patient.setDateOfBirth("28");
        patient.setPhone(" 555-0100 ");
        return patient;
    }

    private static Properties post() {
        Properties post = new Properties();
        post.setProperty("demographic_no", Integer.toString(PATIENT));
        post.setProperty("formCreated", "2024/02/29");
        post.setProperty("studyID", "N/A");
        return post;
    }

    /** The prefill each retired record class set for a new form, besides the patient, date and study ID. */
    private static final Map<String, Set<String>> LEGACY_PREFILL = Map.ofEntries(
            Map.entry("2MinWalk", Set.of("dob", "sex", "phone")), Map.entry("CESD", Set.of()),
            Map.entry("Caregiver", Set.of()), Map.entry("CostQuestionnaire", Set.of("dob", "sex", "phone")),
            Map.entry("Falls", Set.of()), Map.entry("GripStrength", Set.of()),
            Map.entry("HomeFalls", Set.of("dob", "sex", "phone")), Map.entry("IntakeInfo", Set.of("dob", "sex", "phone")),
            Map.entry("InternetAccess", Set.of("dob", "sex", "phone")),
            Map.entry("LateLifeFDIDisability", Set.of("dob", "sex", "phone")),
            Map.entry("LateLifeFDIFunction", Set.of("dob", "sex", "phone")), Map.entry("SF36", Set.of("dob", "sex", "phone")),
            Map.entry("SF36Caregiver", Set.of("dob", "sex", "phone")),
            Map.entry("SatisfactionScale", Set.of("dob", "sex", "phone")), Map.entry("SelfAdministered", Set.of("dob", "sex")),
            Map.entry("SelfEfficacy", Set.of("dob", "sex", "phone")), Map.entry("SelfManagement", Set.of("dob", "sex", "phone")),
            Map.entry("TreatmentPref", Set.of("dob", "sex", "phone")));

    @Test
    void shouldPrefillExactlyTheRetiredRecordsFields_whenCreatingEachNewForm() {
        when(demographics.getDemographic(actor, PATIENT)).thenReturn(patient());
        assertThat(LEGACY_PREFILL.keySet()).containsExactlyInAnyOrderElementsOf(
                RehabStudyForms.all().stream().map(RehabStudyFormType::formClass).collect(Collectors.toSet()));
        for (RehabStudyFormType<?> type : RehabStudyForms.all()) {
            Properties loaded = service.load(actor, type, PATIENT, 0);
            Set<String> expected = LEGACY_PREFILL.get(type.formClass());
            assertThat(loaded.stringPropertyNames()).as(type.formClass())
                    .containsExactlyInAnyOrderElementsOf(union(expected, "demographic_no", "formCreated", "studyID"));
            assertThat(loaded.getProperty("demographic_no")).isEqualTo("123");
            assertThat(loaded.getProperty("formCreated")).matches("\\d{4}/\\d{2}/\\d{2}");
            assertThat(loaded.getProperty("studyID")).isEqualTo("N/A");
            if (expected.contains("dob")) assertThat(loaded.getProperty("dob")).isEqualTo("1950/02/28");
            if (expected.contains("sex")) assertThat(loaded.getProperty("sex")).isEqualTo("F");
            if (expected.contains("phone")) assertThat(loaded.getProperty("phone")).isEqualTo("555-0100");
        }
    }

    private static Set<String> union(Set<String> fields, String... more) {
        var all = new java.util.HashSet<>(fields);
        all.addAll(Set.of(more));
        return all;
    }

    @Test
    void shouldShowOnlyTheStudyId_whenThePatientRecordIsMissing() {
        when(forms.findStudyId(PATIENT)).thenReturn(Optional.of("12"));
        Properties loaded = service.load(actor, type("SF36"), PATIENT, 0);
        assertThat(loaded).containsOnlyKeys("studyID");
        assertThat(loaded.getProperty("studyID")).isEqualTo("12");
    }

    @Test
    void shouldReadOnlyTheSavedVersion_whenReopening() {
        FormSF36 saved = new FormSF36();
        saved.setDemographicNo(PATIENT);
        saved.setStudyId("12");
        ReflectionTestUtils.setField(saved, "id", 44);
        when(forms.findForPatient(FormSF36.class, PATIENT, 44)).thenReturn(saved);
        Properties loaded = service.load(actor, type("SF36"), PATIENT, 44);
        assertThat(loaded.getProperty("ID")).isEqualTo("44");
        assertThat(loaded.getProperty("studyID")).isEqualTo("12");
        verifyNoInteractions(demographics);
        verify(forms, never()).findStudyId(anyInt());
    }

    @Test
    void shouldRefuse_whenTheVersionIsMissingOrAnotherPatients() {
        Properties values = post();
        assertThatThrownBy(() -> service.load(actor, type("SF36"), PATIENT, 44)).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_form)");
        assertThatThrownBy(() -> service.saveVersion(actor, type("SF36"), PATIENT, 44, values))
                .isInstanceOf(SecurityException.class);
        verify(forms, never()).persist(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"_form", "_demographic"})
    void shouldRefuseBeforeReadingOrWriting_whenAPrivilegeIsDenied(String object) {
        Properties values = post();
        when(security.hasPrivilege(eq(actor), eq(object), anyString(), eq("123"))).thenReturn(false);
        assertThatThrownBy(() -> service.load(actor, type("SF36"), PATIENT, 44)).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (" + object + ")");
        assertThatThrownBy(() -> service.load(actor, type("SF36"), PATIENT, 0)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.saveVersion(actor, type("SF36"), PATIENT, 44, values))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(forms, demographics);
    }

    @Test
    void shouldRefuseBeforeReadingOrWriting_whenPatientAccessIsDenied() {
        Properties values = post();
        when(security.isAllowedAccessToPatientRecord(actor, PATIENT)).thenReturn(false);
        assertThatThrownBy(() -> service.load(actor, type("SF36"), PATIENT, 0)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.saveVersion(actor, type("SF36"), PATIENT, 0, values))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(forms, demographics);
    }

    @Test
    void shouldRejectAbsentActorAndInvalidPatient_beforeAnyPersistence() {
        Properties values = post();
        assertThatThrownBy(() -> service.load(null, type("SF36"), PATIENT, 0)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.saveVersion(null, type("SF36"), PATIENT, 0, values))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.load(actor, type("SF36"), 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.saveVersion(actor, type("SF36"), -1, 0, values))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(forms, demographics);
    }

    @Test
    void shouldBindThePatientAndActorAndRequireOnlyDemographicRead_whenSaving() {
        Properties values = post();
        values.setProperty("demographic_no", "999");
        values.setProperty("provider_no", "123456");
        values.setProperty("ID", "2147483647");
        values.setProperty("Q1Ex", "on");
        when(security.hasPrivilege(actor, "_demographic", "w", "123")).thenReturn(false);
        doAnswer(call -> {
            RehabStudyAssessment saved = call.getArgument(0);
            assertThat(saved).isInstanceOf(FormSF36.class);
            assertThat(saved.getId()).isNull();
            assertThat(saved.getDemographicNo()).isEqualTo(PATIENT);
            assertThat(saved.getProviderNo()).isEqualTo(999998);
            assertThat(saved.getStudyId()).isEqualTo("N/A");
            assertThat(saved.getFormEdited().getTime() % 1000).as("whole seconds").isZero();
            ReflectionTestUtils.setField(saved, "id", 42);
            return null;
        }).when(forms).persist(any());
        assertThat(service.saveVersion(actor, type("SF36"), PATIENT, 0, values)).isEqualTo(42);
        verify(forms).flush();
        verify(security).hasPrivilege(actor, "_form", "w", "123");
        verify(security).hasPrivilege(actor, "_demographic", "r", "123");
        verifyNoInteractions(demographics);
    }

    @Test
    void shouldCheckTheSourceIsThisPatientsVersion_beforeSavingFromIt() {
        FormIntakeInfo source = new FormIntakeInfo();
        when(forms.findForPatient(FormIntakeInfo.class, PATIENT, 7)).thenReturn(source);
        doAnswer(call -> {
            RehabStudyAssessment saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 8);
            return null;
        }).when(forms).persist(any());
        assertThat(service.saveVersion(actor, type("IntakeInfo"), PATIENT, 7, post())).isEqualTo(8);
        verify(forms).findForPatient(FormIntakeInfo.class, PATIENT, 7);
    }

    @Test
    void shouldRefuseToSave_withoutAStudyId() {
        Properties values = post();
        values.remove("studyID");
        assertThatThrownBy(() -> service.saveVersion(actor, type("SF36"), PATIENT, 0, values))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A study identifier is required");
        verify(forms, never()).persist(any());
    }

    @Test
    void shouldRefuseToSave_whenTheProviderNumberIsNotNumeric() {
        when(actor.getLoggedInProviderNo()).thenReturn("abc");
        Properties values = post();
        assertThatThrownBy(() -> service.saveVersion(actor, type("SF36"), PATIENT, 0, values))
                .isInstanceOf(NumberFormatException.class);
        verify(forms, never()).persist(any());
    }
}
