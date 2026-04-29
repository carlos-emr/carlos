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
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Handles the optional RA-file import requested by {@code documentBean}.
 *
 * <p>The import is a filesystem/database side effect, so the action invokes this
 * service before the read-only {@code OntarioRAViewModelAssembler} builds the view
 * model.</p>
 */
@Service
public class OntarioRAImportService {

    private final BillingONRemittanceAdviceService remittanceAdviceService;

    public OntarioRAImportService(BillingONRemittanceAdviceService remittanceAdviceService) {
        this.remittanceAdviceService = remittanceAdviceService;
    }

    public void importDocumentBeanFile(HttpServletRequest request) {
        Object dbAttr = request.getAttribute("documentBean");
        if (!(dbAttr instanceof DocumentBean documentBean)) {
            return;
        }
        String filename = documentBean.getFilename();
        if (filename == null || filename.isEmpty()) {
            return;
        }

        try {
            String documentDir = CarlosProperties.getInstance()
                    .getProperty("DOCUMENT_DIR", "").trim();
            if (documentDir.isEmpty()) {
                MiscUtils.getLogger().warn("Skipping RA import because DOCUMENT_DIR is not configured");
                return;
            }
            File safeFile = PathValidationUtils.validatePath(filename, new File(documentDir));
            remittanceAdviceService.importRAFile(safeFile.getPath());
        } catch (SecurityException e) {
            MiscUtils.getLogger().warn(
                    "Blocked unsafe RA import filename '{}'",
                    LogSanitizer.sanitize(filename));
        } catch (Exception e) {
            MiscUtils.getLogger().error(
                    "Failed to import RA file: {}",
                    LogSanitizer.sanitize(filename), e);
        }
    }
}
