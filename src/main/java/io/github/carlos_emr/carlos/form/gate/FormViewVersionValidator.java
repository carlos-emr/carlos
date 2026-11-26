/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form.gate;

import io.github.carlos_emr.carlos.form.Growth0To36FormService;
import io.github.carlos_emr.carlos.form.RehabStudyFormService;
import io.github.carlos_emr.carlos.form.RehabStudyForms;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.function.Supplier;

/**
 * Refuses a typed form page's record before the page renders (issue #5693).
 *
 * <p>The Growth 0–36 and rehabilitation-study pages load their record through a service that
 * refuses another patient's or a missing version, or a patient the actor may not see. Thrown
 * while the page renders, that refusal is a page failure: the container logs it as SEVERE and,
 * unless the gate catches it in time, answers 500. Asked here first, it is an ordinary
 * {@link SecurityException} that the form package maps to its 403 security page.</p>
 *
 * <p>Other pages are untouched, and the services are looked up only for a typed page. A
 * parameter the page itself cannot read is left to the page, which fails on it as before.</p>
 *
 * @since 2026-11-26
 */
public final class FormViewVersionValidator {
    static final String GROWTH_VIEW = "formGrowth0_36";

    private final Supplier<RehabStudyFormService> rehabStudyForms;
    private final Supplier<Growth0To36FormService> growthForms;

    /** Resolves the Spring service proxies on first use. */
    public FormViewVersionValidator() {
        this(() -> SpringUtils.getBean(RehabStudyFormService.class), () -> SpringUtils.getBean(Growth0To36FormService.class));
    }

    FormViewVersionValidator(Supplier<RehabStudyFormService> rehabStudyForms, Supplier<Growth0To36FormService> growthForms) {
        this.rehabStudyForms = rehabStudyForms;
        this.growthForms = growthForms;
    }

    /**
     * @param view the {@code form/<view>} route name
     * @param actor the authenticated caller
     * @param patient the {@code demographic_no} parameter, as the page reads it
     * @param formId the {@code formId} parameter, as the page reads it
     * @throws SecurityException if the page's service would refuse the record
     */
    public void requireReadable(String view, LoggedInInfo actor, String patient, String formId) {
        var rehabStudyForm = RehabStudyForms.forView(view);
        if (rehabStudyForm.isEmpty() && !GROWTH_VIEW.equals(view)) return;
        Integer patientNo = parse(patient);
        Integer id = parse(formId);
        // The pages parse both with Integer.parseInt; anything else fails there, as it always has.
        if (patientNo == null || id == null || patientNo <= 0) return;
        if (rehabStudyForm.isPresent()) {
            rehabStudyForms.get().requireReadable(actor, rehabStudyForm.get(), patientNo, id);
        } else {
            growthForms.get().requireReadable(actor, patientNo, id);
        }
    }

    private static Integer parse(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
