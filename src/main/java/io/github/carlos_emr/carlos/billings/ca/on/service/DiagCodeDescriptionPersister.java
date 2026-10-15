/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.List;

import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Write access for diagnostic-code description edits from
 * {@code billingDigUpdate.jsp}.
 */
@Service
@Transactional
public class DiagCodeDescriptionPersister {

    private final DiagnosticCodeDao diagnosticCodeDao;

    public DiagCodeDescriptionPersister(DiagnosticCodeDao diagnosticCodeDao) {
        this.diagnosticCodeDao = diagnosticCodeDao;
    }

    /**
     * The diagnostic code an Update button's value names. The search page labels
     * each button "{@code <localised Update> <code>}", and codes contain no spaces,
     * so the code is the text after the last space: all of it, whether it has
     * three characters or four. Taking only the last three characters, as this
     * once did, turned {@code 2740} into {@code 740} and rewrote the wrong code.
     * A value with no space (the old {@code update001} form) keeps that legacy
     * last-three-characters reading.
     *
     * @param submitValue the {@code update} request parameter
     * @return the code, or {@code null} when the value cannot carry one
     */
    public static String codeFromSubmitValue(String submitValue) {
        if (submitValue == null) {
            return null;
        }
        int space = submitValue.lastIndexOf(' ');
        if (space >= 0) {
            String code = submitValue.substring(space + 1);
            return code.isEmpty() ? null : code;
        }
        return submitValue.length() < 3 ? null : submitValue.substring(submitValue.length() - 3);
    }

    public boolean updateDescription(String submitValue, String newDescription) {
        String code = codeFromSubmitValue(submitValue);
        if (code == null) {
            throw new DiagDescriptionUpdateException("", "missing diagnostic code");
        }
        if (newDescription == null) {
            // No description field reached the server for this code; writing
            // null would blank the stored text rather than leave it unchanged.
            throw new DiagDescriptionUpdateException(code, "missing description");
        }
        try {
            List<DiagnosticCode> matches = diagnosticCodeDao.findByDiagnosticCode(code);
            if (matches == null || matches.isEmpty()) {
                throw new DiagDescriptionUpdateException(code, "diagnostic code not found");
            }
            for (DiagnosticCode dcode : matches) {
                dcode.setDescription(newDescription);
                diagnosticCodeDao.merge(dcode);
            }
            return true;
        } catch (RuntimeException ex) {
            if (ex instanceof DiagDescriptionUpdateException) {
                throw ex;
            }
            Logger logger = MiscUtils.getLogger();
            if (logger.isErrorEnabled()) {
                logger.error("Diagnostic code update failed; diagnostic code omitted from log; causeType={}",
                        ex.getClass().getName());
            }
            throw new DiagDescriptionUpdateException(code, ex);
        }
    }
}
