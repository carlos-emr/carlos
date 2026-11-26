/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.Objects;
import java.util.Properties;

/**
 * The form router's view of a 2004 rehabilitation-study questionnaire, in place of the retired
 * {@code Frm*Record} class of the same {@code form_class} name.
 *
 * @since 2026-11-26
 */
public class RehabStudyForm extends FrmRecord {
    private final RehabStudyFormType<?> type;
    private final RehabStudyFormService service;

    /**
     * Uses the Spring service proxy so form routing keeps the access checks and transactions.
     *
     * @param type the questionnaire
     */
    public RehabStudyForm(RehabStudyFormType<?> type) {
        this(type, SpringUtils.getBean(RehabStudyFormService.class));
    }

    RehabStudyForm(RehabStudyFormType<?> type, RehabStudyFormService service) {
        this.type = Objects.requireNonNull(type, "type");
        this.service = Objects.requireNonNull(service, "service");
    }

    /** @return the questionnaire this router entry serves */
    public RehabStudyFormType<?> getType() {
        return type;
    }

    @Override
    public Properties getFormRecord(LoggedInInfo actor, int patient, int id) {
        return service.load(actor, type, patient, id);
    }

    /**
     * Saves the post as a new version. The source version is the posted {@code formId}, or
     * {@code ID} when the page posted none.
     */
    @Override
    public int saveFormRecord(LoggedInInfo actor, Properties values) {
        String patient = values.getProperty("demographic_no");
        if (patient == null || patient.isBlank()) throw new IllegalArgumentException("A patient identifier is required");
        String source = values.getProperty("formId");
        if (source == null || source.isBlank()) source = values.getProperty("ID", "0");
        return service.saveVersion(actor, type, Integer.parseInt(patient.trim()), source.isBlank() ? 0
                : Integer.parseInt(source.trim()), values);
    }

    @Override
    public int saveFormRecord(Properties values) {
        throw new IllegalStateException("Rehabilitation study form saves require an authenticated actor");
    }

    @Override
    public String findActionValue(String submit) {
        return new FrmRecordHelp().findActionValue(submit);
    }

    @Override
    public String createActionURL(String where, String action, String patient, String id) {
        return new FrmRecordHelp().createActionURL(where, action, patient, id);
    }
}
