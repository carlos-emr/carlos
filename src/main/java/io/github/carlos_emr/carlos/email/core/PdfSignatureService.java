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
package io.github.carlos_emr.carlos.email.core;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.utility.DocumentManifest;
import io.github.carlos_emr.carlos.utility.PDFSigningConfig;
import io.github.carlos_emr.carlos.utility.PDFSigningUtil;

/**
 * The injectable face of the static PDF signing code: reads the signing configuration from
 * {@code carlos.properties} and delegates to {@link PDFSigningUtil}.
 *
 * <p>It holds no logic of its own. It exists so that {@link PdfAttachmentSigningService}, which
 * owns the per-attachment sign, adopt and clean-up sequence of an email send, can be tested with
 * a mock in place of static mocking (carlos-emr/carlos#3851). Every method passes its arguments
 * through unchanged, so the contracts are those of {@link PDFSigningConfig#fromCarlosProperties()}
 * and {@link PDFSigningUtil}.</p>
 *
 * @since 2026-11-19
 */
@Service
public class PdfSignatureService {

    /**
     * Reads the signing configuration as it stands now. Not cached: each send reads it afresh,
     * as the send path did before this service existed.
     *
     * @return the current configuration, which may be disabled
     */
    public PDFSigningConfig currentConfiguration() {
        return PDFSigningConfig.fromCarlosProperties();
    }

    /**
     * Adds a detached signature to a PDF, leaving the source untouched. See
     * {@link PDFSigningUtil#signPDF(Path, PDFSigningConfig, String, DocumentManifest)}.
     *
     * @param pdfPath the PDF to sign
     * @param config an enabled, complete signing configuration
     * @param ownerPassword the password the PDF opens with, or {@code null} when it has none
     * @param manifest the document manifest to embed with the signature, or {@code null}
     * @return a new owner-only temp file the caller must adopt or delete
     * @throws IOException when the keystore or PDF cannot be read, or signing fails
     */
    public Path sign(Path pdfPath, PDFSigningConfig config, String ownerPassword, DocumentManifest manifest)
            throws IOException {
        return PDFSigningUtil.signPDF(pdfPath, config, ownerPassword, manifest);
    }

    /**
     * The configured signing key's ID, as the audit log records it. See
     * {@link PDFSigningUtil#signingKeyId(PDFSigningConfig)}.
     *
     * @param config an enabled, complete signing configuration
     * @return the key ID
     * @throws IOException when the keystore cannot be loaded or validated
     */
    public String signingKeyId(PDFSigningConfig config) throws IOException {
        return PDFSigningUtil.signingKeyId(config);
    }
}
