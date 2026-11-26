/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.form.RehabStudyFormType.Prefill;
import io.github.carlos_emr.carlos.form.model.rehab.Form2MinWalk;
import io.github.carlos_emr.carlos.form.model.rehab.FormCESD;
import io.github.carlos_emr.carlos.form.model.rehab.FormCaregiver;
import io.github.carlos_emr.carlos.form.model.rehab.FormCostQuestionnaire;
import io.github.carlos_emr.carlos.form.model.rehab.FormFalls;
import io.github.carlos_emr.carlos.form.model.rehab.FormGripStrength;
import io.github.carlos_emr.carlos.form.model.rehab.FormHomeFalls;
import io.github.carlos_emr.carlos.form.model.rehab.FormIntakeInfo;
import io.github.carlos_emr.carlos.form.model.rehab.FormInternetAccess;
import io.github.carlos_emr.carlos.form.model.rehab.FormLateLifeFDIDisability;
import io.github.carlos_emr.carlos.form.model.rehab.FormLateLifeFDIFunction;
import io.github.carlos_emr.carlos.form.model.rehab.FormSF36;
import io.github.carlos_emr.carlos.form.model.rehab.FormSF36Caregiver;
import io.github.carlos_emr.carlos.form.model.rehab.FormSatisfactionScale;
import io.github.carlos_emr.carlos.form.model.rehab.FormSelfAdministered;
import io.github.carlos_emr.carlos.form.model.rehab.FormSelfEfficacy;
import io.github.carlos_emr.carlos.form.model.rehab.FormSelfManagement;
import io.github.carlos_emr.carlos.form.model.rehab.FormTreatmentPref;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The eighteen 2004 rehabilitation-study questionnaires that replaced their {@code Frm*Record}
 * classes (issue #5693).
 *
 * <p>Each new form shows the patient number, today's date and the patient's study ID
 * ({@code "N/A"} when not enrolled). The prefill sets reproduce the retired record classes
 * exactly: most also showed birth date, sex and phone; four showed none; Self-Administered
 * showed birth date and sex. Only Intake Information and Self-Administered store those values,
 * in their own {@code dob}, {@code sex} and {@code phone} columns.</p>
 *
 * @since 2026-11-26
 */
public final class RehabStudyForms {
    private static final Set<Prefill> BIRTH_SEX_PHONE = EnumSet.allOf(Prefill.class);
    private static final Set<Prefill> NONE = EnumSet.noneOf(Prefill.class);
    private static final Set<Prefill> BIRTH_SEX = EnumSet.of(Prefill.BIRTH_DATE, Prefill.SEX);

    private static final List<RehabStudyFormType<?>> TYPES = List.of(
            new RehabStudyFormType<>("2MinWalk", "form2minwalk", "form2MinWalk", Form2MinWalk.class, Form2MinWalk::new,
                    Form2MinWalk.columns(), BIRTH_SEX_PHONE),
            new RehabStudyFormType<>("CESD", "formCESD", "formCESD", FormCESD.class, FormCESD::new,
                    FormCESD.columns(), NONE),
            new RehabStudyFormType<>("Caregiver", "formcaregiver", "formCaregiver", FormCaregiver.class, FormCaregiver::new,
                    FormCaregiver.columns(), NONE),
            new RehabStudyFormType<>("CostQuestionnaire", "formcostquestionnaire", "formCostQuestionnaire", FormCostQuestionnaire.class,
                    FormCostQuestionnaire::new, FormCostQuestionnaire.columns(), BIRTH_SEX_PHONE),
            new RehabStudyFormType<>("Falls", "formfalls", "formFalls", FormFalls.class, FormFalls::new,
                    FormFalls.columns(), NONE),
            new RehabStudyFormType<>("GripStrength", "formgripstrength", "formGripStrength", FormGripStrength.class,
                    FormGripStrength::new, FormGripStrength.columns(), NONE),
            new RehabStudyFormType<>("HomeFalls", "formhomefalls", "formHomeFalls", FormHomeFalls.class, FormHomeFalls::new,
                    FormHomeFalls.columns(), BIRTH_SEX_PHONE),
            new RehabStudyFormType<>("IntakeInfo", "formintakeinfo", "formIntakeInfo", FormIntakeInfo.class, FormIntakeInfo::new,
                    FormIntakeInfo.columns(), BIRTH_SEX_PHONE),
            new RehabStudyFormType<>("InternetAccess", "formInternetAccess", "formInternetAccess", FormInternetAccess.class,
                    FormInternetAccess::new, FormInternetAccess.columns(), BIRTH_SEX_PHONE),
            new RehabStudyFormType<>("LateLifeFDIDisability", "formlatelifeFDIdisability", "formLateLifeFDIDisability",
                    FormLateLifeFDIDisability.class, FormLateLifeFDIDisability::new,
                    FormLateLifeFDIDisability.columns(), BIRTH_SEX_PHONE),
            new RehabStudyFormType<>("LateLifeFDIFunction", "formlatelifeFDIfunction", "formLateLifeFDIFunction",
                    FormLateLifeFDIFunction.class, FormLateLifeFDIFunction::new,
                    FormLateLifeFDIFunction.columns(), BIRTH_SEX_PHONE),
            new RehabStudyFormType<>("SF36", "formSF36", "formSF36", FormSF36.class, FormSF36::new,
                    FormSF36.columns(), BIRTH_SEX_PHONE),
            new RehabStudyFormType<>("SF36Caregiver", "formSF36caregiver", "formSF36Caregiver", FormSF36Caregiver.class,
                    FormSF36Caregiver::new, FormSF36Caregiver.columns(), BIRTH_SEX_PHONE),
            new RehabStudyFormType<>("SatisfactionScale", "formSatisfactionScale", "formSatisfactionScale", FormSatisfactionScale.class,
                    FormSatisfactionScale::new, FormSatisfactionScale.columns(), BIRTH_SEX_PHONE),
            new RehabStudyFormType<>("SelfAdministered", "formselfadministered", "formSelfAdministered", FormSelfAdministered.class,
                    FormSelfAdministered::new, FormSelfAdministered.columns(), BIRTH_SEX),
            new RehabStudyFormType<>("SelfEfficacy", "formselfefficacy", "formSelfEfficacy", FormSelfEfficacy.class,
                    FormSelfEfficacy::new, FormSelfEfficacy.columns(), BIRTH_SEX_PHONE),
            new RehabStudyFormType<>("SelfManagement", "formselfmanagement", "formSelfManagement", FormSelfManagement.class,
                    FormSelfManagement::new, FormSelfManagement.columns(), BIRTH_SEX_PHONE),
            new RehabStudyFormType<>("TreatmentPref", "formtreatmentpref", "formTreatmentPref", FormTreatmentPref.class,
                    FormTreatmentPref::new, FormTreatmentPref.columns(), BIRTH_SEX_PHONE));

    private RehabStudyForms() { }

    /** @return every registered questionnaire, in form-class order */
    public static List<RehabStudyFormType<?>> all() {
        return TYPES;
    }

    /**
     * @param formClass the router's exact, case-sensitive {@code form_class} name; may be null
     * @return the questionnaire, or empty when the name is not a rehabilitation-study form
     */
    public static Optional<RehabStudyFormType<?>> forFormClass(String formClass) {
        return TYPES.stream().filter(type -> type.formClass().equals(formClass)).findFirst();
    }

    /**
     * @param view the exact, case-sensitive {@code form/<view>} route name; may be null
     * @return the questionnaire whose page that is, or empty for any other page
     */
    public static Optional<RehabStudyFormType<?>> forView(String view) {
        return TYPES.stream().filter(type -> type.view().equals(view)).findFirst();
    }
}
