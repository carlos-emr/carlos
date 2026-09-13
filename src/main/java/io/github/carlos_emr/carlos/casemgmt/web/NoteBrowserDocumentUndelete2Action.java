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
package io.github.carlos_emr.carlos.casemgmt.web;

import org.apache.struts2.interceptor.parameter.StrutsParameter;

import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.utility.LogSafe;

/**
 * Restores a deleted note-browser document through the POST-only mutation action flow.
 *
 * <p>The shared {@link AbstractNoteBrowserDocumentMutation2Action} contract rejects
 * non-POST requests, requires an authenticated user with {@code _eChart w}, and
 * validates the document number before this action calls {@link EDocUtil}.</p>
 *
 * @since 2026-06-01
 */
public class NoteBrowserDocumentUndelete2Action extends AbstractNoteBrowserDocumentMutation2Action {

    private String undelDocumentNo;

    @Override
    protected boolean hasMutationParameter() {
        return undelDocumentNo != null && !undelDocumentNo.isEmpty();
    }

    @Override
    protected boolean hasValidMutationParameters() {
        return isPositiveInteger(undelDocumentNo);
    }

    @Override
    protected String invalidParameterMessage() {
        return "invalid undelDocumentNo";
    }

    @Override
    protected void mutateDocument() throws DocumentMutationException {
        undeleteDocument(undelDocumentNo);
    }

    @Override
    protected boolean handlesMutationException() {
        return true;
    }

    @Override
    protected String logMessage() {
        return "noteBrowser undeleteDocument failed docNo=" + LogSafe.sanitize(undelDocumentNo);
    }

    protected void undeleteDocument(String docNo) throws DocumentMutationException {
        try {
            EDocUtil.undeleteDocument(docNo);
        } catch (Exception e) {
            throw new DocumentMutationException(e);
        }
    }

    public String getUndelDocumentNo() { return undelDocumentNo; }
    @StrutsParameter public void setUndelDocumentNo(String v) { this.undelDocumentNo = v; }
}
