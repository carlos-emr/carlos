/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The form router reaches every rehabilitation-study questionnaire through the typed service,
 * under the {@code form_class} names its pages and encounter registrations already post.
 *
 * @since 2026-11-26
 */
@Tag("unit")
@Tag("form")
@DisplayName("Rehabilitation study form routing")
class RehabStudyFormsUnitTest extends CarlosUnitTestBase {
    private static final List<String> FORM_CLASSES = List.of("2MinWalk", "CESD", "Caregiver", "CostQuestionnaire",
            "Falls", "GripStrength", "HomeFalls", "IntakeInfo", "InternetAccess", "LateLifeFDIDisability",
            "LateLifeFDIFunction", "SF36", "SF36Caregiver", "SatisfactionScale", "SelfAdministered", "SelfEfficacy",
            "SelfManagement", "TreatmentPref");

    private RehabStudyFormService service;
    private LoggedInInfo actor;

    @BeforeEach
    void registerBeans() {
        // FrmRecord's constructor looks both up; the questionnaire router entry uses neither.
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));
        service = createAndRegisterMock(RehabStudyFormService.class);
        actor = mock(LoggedInInfo.class);
    }

    @Test
    void shouldRegisterTheEighteenRetiredRecordNames_eachOnceAndAllowedByTheRouter() {
        assertThat(RehabStudyForms.all()).extracting(RehabStudyFormType::formClass)
                .containsExactlyElementsOf(FORM_CLASSES);
        assertThat(RehabStudyForms.all()).extracting(RehabStudyFormType::table).doesNotHaveDuplicates();
        assertThat(RehabStudyForms.all()).extracting(RehabStudyFormType::entity).doesNotHaveDuplicates();
        assertThat(FrmRecordFactory.ALLOWED_FORM_CLASSES).containsAll(FORM_CLASSES);
        for (String formClass : FORM_CLASSES) {
            assertThat(RehabStudyForms.forFormClass(formClass).orElseThrow().table())
                    .isEqualToIgnoringCase("form" + formClass);
        }
    }

    @Test
    void shouldRemoveEveryRetiredRecordClass() {
        for (String formClass : FORM_CLASSES) {
            String retired = "io.github.carlos_emr.carlos.form.Frm" + formClass + "Record";
            assertThatThrownBy(() -> Class.forName(retired, false, getClass().getClassLoader()))
                    .as(retired).isInstanceOf(ClassNotFoundException.class);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"sf36", "SF36 ", "Rourke2020", "RhImmuneGlobulin", "FrmSF36Record"})
    void shouldNotMatchOtherNames_exactlyAsTheRouterDoes(String formClass) {
        assertThat(RehabStudyForms.forFormClass(formClass)).isEmpty();
    }

    @Test
    void shouldRouteEachQuestionnaire_toTheTypedServiceEntry() {
        for (String formClass : FORM_CLASSES) {
            FrmRecord routed = new FrmRecordFactory().factory(formClass);
            assertThat(routed).as(formClass).isInstanceOf(RehabStudyForm.class);
            assertThat(((RehabStudyForm) routed).getType()).isSameAs(RehabStudyForms.forFormClass(formClass).orElseThrow());
        }
        assertThat(new FrmRecordFactory().factory("RhImmuneGlobulin")).isInstanceOf(FrmRhImmuneGlobulinRecord.class);
    }

    @Test
    void shouldLoadThroughTheService_withTheRoutedQuestionnaire() throws Exception {
        RehabStudyFormType<?> type = RehabStudyForms.forFormClass("Falls").orElseThrow();
        Properties loaded = new Properties();
        when(service.load(actor, type, 5, 9)).thenReturn(loaded);
        assertThat(new RehabStudyForm(type).getFormRecord(actor, 5, 9)).isSameAs(loaded);
    }

    @Test
    void shouldSaveFromThePostedFormIdOrId_asTheSourceVersion() throws Exception {
        RehabStudyFormType<?> type = RehabStudyForms.forFormClass("Falls").orElseThrow();
        RehabStudyForm form = new RehabStudyForm(type);
        Properties post = new Properties();
        post.setProperty("demographic_no", " 5 ");
        post.setProperty("formId", " 9 ");
        when(service.saveVersion(actor, type, 5, 9, post)).thenReturn(10);
        assertThat(form.saveFormRecord(actor, post)).isEqualTo(10);

        Properties byId = new Properties();
        byId.setProperty("demographic_no", "5");
        byId.setProperty("formId", "");
        byId.setProperty("ID", "11");
        when(service.saveVersion(actor, type, 5, 11, byId)).thenReturn(12);
        assertThat(form.saveFormRecord(actor, byId)).isEqualTo(12);

        Properties fresh = new Properties();
        fresh.setProperty("demographic_no", "5");
        when(service.saveVersion(actor, type, 5, 0, fresh)).thenReturn(13);
        assertThat(form.saveFormRecord(actor, fresh)).isEqualTo(13);
        verify(service).saveVersion(actor, type, 5, 0, fresh);
    }

    @Test
    void shouldRefuseSaves_withoutAnActorOrAPatient() {
        RehabStudyForm form = new RehabStudyForm(RehabStudyForms.forFormClass("SF36").orElseThrow());
        Properties post = new Properties();
        assertThatThrownBy(() -> form.saveFormRecord(post)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> form.saveFormRecord(actor, post)).isInstanceOf(IllegalArgumentException.class);
        post.setProperty("demographic_no", " ");
        assertThatThrownBy(() -> form.saveFormRecord(actor, post)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }

    @Test
    void shouldKeepTheLegacyActionNavigation() throws Exception {
        RehabStudyForm form = new RehabStudyForm(RehabStudyForms.forFormClass("SF36").orElseThrow());
        assertThat(form.findActionValue("save")).isEqualTo("save");
        assertThat(form.findActionValue("exit")).isEqualTo("exit");
        assertThat(form.findActionValue("bogus")).isEqualTo("failure");
        assertThat(form.createActionURL("save", "save", "5", "9")).isEqualTo("save?demographic_no=5&formId=9");
        verify(service, org.mockito.Mockito.never()).load(any(), any(), anyInt(), anyInt());
    }
}
