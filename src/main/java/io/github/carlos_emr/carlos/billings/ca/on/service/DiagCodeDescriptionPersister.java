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

    public boolean updateDescription(String submitValue, String newDescription) {
        if (submitValue == null || submitValue.length() < 3) {
            throw new DiagDescriptionUpdateException("", "missing diagnostic code");
        }
        String code = submitValue.substring(submitValue.length() - 3);
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
