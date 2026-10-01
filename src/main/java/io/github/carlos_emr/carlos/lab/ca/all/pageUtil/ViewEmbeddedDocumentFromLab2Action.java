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
 *
 * The inline lab PDF display is adapted from open-osp/Open-O commits
 * 4b5a3d62e6 and b63af33e90 (GPL); this CARLOS implementation adds the
 * per-OBX detection, PDF signature check and size handling below.
 */
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.lab.service.LabPdfPreviewSettings;
import io.github.carlos_emr.carlos.lab.service.LabPdfPreviewSettingsService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/**
 * Serves the PDF embedded in an HL7 {@code ED} OBX {@code inline}, for the preview frame on the
 * lab display ({@code lab/ViewEmbeddedDocumentFromLab}). Read-only: GET and HEAD only.
 *
 * <p>Honours the lab display preferences in {@link LabPdfPreviewSettings}: with the preview
 * turned off the route answers 404, and a PDF over the size limit answers 413 (the page shows a
 * "use Download PDF" message instead of a frame). See {@link AbstractEmbeddedLabDocumentAction}
 * for the authorization and content contract shared with the download route.</p>
 *
 * @since 2026-09-30
 */
public class ViewEmbeddedDocumentFromLab2Action extends AbstractEmbeddedLabDocumentAction {

    private final transient LabPdfPreviewSettingsService previewSettingsService;
    private transient LabPdfPreviewSettings settings;

    public ViewEmbeddedDocumentFromLab2Action(SecurityInfoManager securityInfoManager,
            Hl7TextMessageDao hl7TextMessageDao, PatientLabRoutingDao patientLabRoutingDao,
            LabPdfPreviewSettingsService previewSettingsService) {
        super(securityInfoManager, hl7TextMessageDao, patientLabRoutingDao);
        this.previewSettingsService = previewSettingsService;
    }

    @Override
    protected String disposition() {
        return "inline";
    }

    @Override
    protected boolean enabled() {
        return settings().inlinePreviewEnabled();
    }

    @Override
    protected long maxBytes() {
        return settings().maxBytes();
    }

    private LabPdfPreviewSettings settings() {
        // Actions are prototype-scoped, so this reads the preferences once per request.
        if (settings == null) {
            settings = previewSettingsService.load();
        }
        return settings;
    }
}
