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
 * Ported from the openo-beta/Open-O tickler attachment component
 * (PR #2491, Sebastian Ibanez) and adapted for CARLOS.
 */
package io.github.carlos_emr.carlos.documentManager.data;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;

/**
 * One tickler attachment as rendered in the Add/Edit Tickler windows: its type, id, the
 * originating lab source for labs, and a display name.
 *
 * <p>{@link #isViewable()} is {@code false} when the current user lacks read rights on the
 * attachment's type for the patient. The row is still listed, so the assignee can see that
 * something is attached, but {@link #getDisplayName()} is then {@code null} and the view
 * must render a generic "restricted" label rather than the item's name.</p>
 *
 * @since 2026-09-26
 */
public class TicklerAttachmentData {
    private final DocumentType documentType;
    private final String documentId;
    private final String labType;
    private final String displayName;
    private final boolean viewable;

    public TicklerAttachmentData(DocumentType documentType, String documentId, String labType,
                                 String displayName, boolean viewable) {
        this.documentType = documentType;
        this.documentId = documentId;
        this.labType = labType;
        this.displayName = displayName;
        this.viewable = viewable;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getLabType() {
        return labType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isViewable() {
        return viewable;
    }

    /** The picker checkbox name this attachment pre-checks ({@code docNo}, {@code labNo}, ...). */
    public String getParameterName() {
        return TicklerAttachmentParameters.parameterName(documentType);
    }
}
