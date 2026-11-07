/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.form.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;

import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.util.JDBCUtil;
import io.github.carlos_emr.carlos.util.JDBCUtil.XmlImportException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One transaction owns every row of a legacy form archive. */
@Service
@Transactional(rollbackFor = Exception.class)
public class FormXmlImportService {
    private final EncounterFormDao forms;

    public FormXmlImportService(EncounterFormDao forms) {
        this.forms = forms;
    }

    /** @return generated row ID, or null when the same patient/timestamp already exists */
    public Long importEntry(InputStream input, String name) throws XmlImportException {
        JDBCUtil.FormImportRequest request = JDBCUtil.readImportRequest(input, name);
        var target = request.target();
        if (forms.importedRowExists(target.formName(), target.demographicNo(), target.timeStamp())) return null;
        return forms.insertImportedRow(target.formName(), target.demographicNo(), target.timeStamp(), request.values());
    }

    /** The caller owns the ZipFile; every entry stream is closed here, including on failure. */
    public int importArchive(ZipFile archive) throws IOException, XmlImportException {
        int imported = 0;
        var entries = archive.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            try (InputStream input = archive.getInputStream(entry)) {
                importEntry(input, entry.getName());
                imported++;
            }
        }
        return imported;
    }
}
