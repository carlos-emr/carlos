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

import java.io.File;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.DocumentBean;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Handles the optional RA-file import requested by {@code documentBean}.
 *
 * <p>The import is a filesystem/database side effect, so the action invokes this
 * service before the read-only {@code OnRaViewModelAssembler} builds the view
 * model.</p>
 */
@Service
public class OnRaImportService {

    private final BillingOnRaService remittanceAdviceService;

    public enum ImportOutcome {
        NOT_REQUESTED(true),
        EMPTY_FILENAME(true),
        IMPORTED(true),
        DOCUMENT_DIR_MISSING(false),
        BLOCKED_PATH(false),
        IMPORT_FAILED(false);

        private final boolean ok;

        ImportOutcome(boolean ok) {
            this.ok = ok;
        }

        public boolean ok() {
            return ok;
        }
    }

    public OnRaImportService(BillingOnRaService remittanceAdviceService) {
        this.remittanceAdviceService = remittanceAdviceService;
    }

    /**
     * Optionally import the RA file referenced by the request's
     * {@code documentBean} attribute. Failures here must be observable to the
     * caller — a payments-import that silently fails leaves
     * {@code billing_on_payment} out of sync with reality and reconciliation
     * drifts.
     *
     * @return {@code true} when no import was requested or the import
     *         succeeded; {@code false} when an import was attempted but
     *         was blocked, misconfigured, or threw. Callers should surface
     *         {@code false} to the user rather than silently rendering the
     *         post-import view.
     */
    public boolean importDocumentBeanFile(HttpServletRequest request) {
        return importDocumentBeanFileOutcome(request).ok();
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public ImportOutcome importDocumentBeanFileOutcome(HttpServletRequest request) {
        Object dbAttr = request.getAttribute("documentBean");
        if (!(dbAttr instanceof DocumentBean documentBean)) {
            return ImportOutcome.NOT_REQUESTED;
        }
        String filename = documentBean.getFilename();
        if (filename == null || filename.isEmpty()) {
            return ImportOutcome.EMPTY_FILENAME;
        }

        try {
            String documentDir = CarlosProperties.getInstance()
                    .getProperty("DOCUMENT_DIR", "").trim();
            if (documentDir.isEmpty()) {
                MiscUtils.getLogger().warn("Skipping RA import because DOCUMENT_DIR is not configured");
                return ImportOutcome.DOCUMENT_DIR_MISSING;
            }
            File safeFile = PathValidationUtils.validatePath(filename, new File(documentDir));
            remittanceAdviceService.importRAFile(safeFile.getPath());
            return ImportOutcome.IMPORTED;
        } catch (SecurityException e) {
            MiscUtils.getLogger().error(
                    "Blocked unsafe RA import filename '{}'",
                    LogSafe.sanitize(filename), e);
            return ImportOutcome.BLOCKED_PATH;
        } catch (Exception e) {
            MiscUtils.getLogger().error(
                    "Failed to import RA file: {}",
                    LogSafe.sanitize(filename), e);
            return ImportOutcome.IMPORT_FAILED;
        }
    }
}
