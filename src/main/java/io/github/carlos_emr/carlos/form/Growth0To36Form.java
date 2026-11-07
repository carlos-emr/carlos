/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.Properties;

/** Compatibility boundary for the form router and existing Rourke/eForm readers. */
public class Growth0To36Form extends FrmRecord {
    private final Growth0To36FormService service;

    public Growth0To36Form() {
        this(SpringUtils.getBean(Growth0To36FormService.class));
    }

    Growth0To36Form(Growth0To36FormService service) {
        this.service = service;
    }

    @Override
    public Properties getFormRecord(LoggedInInfo actor, int patient, int id) {
        return service.load(actor, patient, id);
    }

    @Override
    public int saveFormRecord(LoggedInInfo actor, Properties values) {
        int patient = Integer.parseInt(values.getProperty("demographic_no").trim());
        String source = values.getProperty("formId");
        if (source == null || source.isBlank()) source = values.getProperty("ID", "0");
        return service.saveVersion(actor, patient, source.isBlank() ? 0 : Integer.parseInt(source.trim()), values);
    }

    @Override
    public int saveFormRecord(Properties values) {
        throw new IllegalStateException("Growth form saves require an authenticated actor");
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
