/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form.gate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.carlos_emr.carlos.form.Growth0To36FormService;
import io.github.carlos_emr.carlos.form.RehabStudyFormService;
import io.github.carlos_emr.carlos.form.RehabStudyForms;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The view gate refuses a typed form page's record before the page renders (issue #5693).
 *
 * @since 2026-11-26
 */
@Tag("unit")
@Tag("form")
@DisplayName("Form view version validator")
class FormViewVersionValidatorUnitTest {
    private RehabStudyFormService rehabStudyForms;
    private Growth0To36FormService growthForms;
    private LoggedInInfo actor;
    private FormViewVersionValidator validator;

    @BeforeEach
    void prepare() {
        rehabStudyForms = mock(RehabStudyFormService.class);
        growthForms = mock(Growth0To36FormService.class);
        actor = mock(LoggedInInfo.class);
        validator = new FormViewVersionValidator(() -> rehabStudyForms, () -> growthForms);
    }

    @Test
    void shouldAskTheQuestionnaireService_beforeARehabilitationStudyPageRenders() {
        var sf36 = RehabStudyForms.forFormClass("SF36").orElseThrow();
        doThrow(new SecurityException("missing required sec object (_form)"))
                .when(rehabStudyForms).requireReadable(actor, sf36, 123, 44);
        assertThatThrownBy(() -> validator.requireReadable("formSF36", actor, "123", "44"))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_form)");
        verifyNoInteractions(growthForms);
    }

    @Test
    void shouldCheckAccessToTheNewFormsPatient_whenNoVersionIsNamed() {
        var intake = RehabStudyForms.forFormClass("IntakeInfo").orElseThrow();
        validator.requireReadable("formintakeinfo", actor, "123", "0");
        verify(rehabStudyForms).requireReadable(actor, intake, 123, 0);
    }

    @Test
    void shouldAskTheGrowthService_beforeTheGrowthPageRenders() {
        validator.requireReadable("formGrowth0_36", actor, "123", "44");
        verify(growthForms).requireReadable(actor, 123, 44);
        verifyNoInteractions(rehabStudyForms);
    }

    @ParameterizedTest(name = "view {0}")
    @CsvSource({"formannual", "formGrowth0_36Print", "formsf36", "formlatelifedisabilityvisualAid1", "''"})
    void shouldLookUpNoService_forAnyOtherPage(String view) {
        Supplier<RehabStudyFormService> unused = () -> { throw new AssertionError("looked up"); };
        Supplier<Growth0To36FormService> unusedGrowth = () -> { throw new AssertionError("looked up"); };
        var untouched = new FormViewVersionValidator(unused, unusedGrowth);
        assertThatCode(() -> untouched.requireReadable(view, actor, "123", "44")).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "patient \"{0}\", formId \"{1}\"")
    @CsvSource(value = {"NULL,44", "123,NULL", "abc,44", "123,4x", "' 123',44", "0,44", "-5,44"}, nullValues = "NULL")
    void shouldLeaveTheParametersThePageCannotReadToThePage(String patient, String formId) {
        validator.requireReadable("formSF36", actor, patient, formId);
        verifyNoInteractions(rehabStudyForms, growthForms);
    }
}
