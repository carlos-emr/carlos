/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.sql.SQLException;
import java.util.Properties;

/** Bridges the legacy form/print dispatch contract to the Spring-managed Growth service. */
public class Growth0To36FormAdapter extends FrmRecord {
    private final Growth0To36FormService service = SpringUtils.getBean(Growth0To36FormService.class);

    /** Loads through the proxied, patient-scoped service. */
    @Override
    public Properties getFormRecord(LoggedInInfo actor, int demographicNo, int existingId) {
        return service.load(actor, demographicNo, existingId);
    }

    /** A migrated form requires the authenticated actor; request properties cannot supply it. */
    @Override
    public int saveFormRecord(Properties values) {
        throw new IllegalStateException("Growth form saves require the authenticated actor");
    }

    /** Saves through the proxy so the action receives an identifier only after commit. */
    @Override
    public int saveFormRecord(LoggedInInfo actor, Properties values) {
        return service.saveVersion(actor, values);
    }

    /** Preserves the shared, non-persistence submit-token mapping. */
    @Override
    public String findActionValue(String submit) throws SQLException {
        return new FrmRecordHelp().findActionValue(submit);
    }

    /** Preserves the shared, non-persistence navigation mapping. */
    @Override
    public String createActionURL(String where, String action, String demoId, String formId) throws SQLException {
        return new FrmRecordHelp().createActionURL(where, action, demoId, formId);
    }
}
