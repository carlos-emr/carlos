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
 * Deletes a note-browser document through the POST-only mutation action flow.
 *
 * <p>The shared {@link AbstractNoteBrowserDocumentMutation2Action} contract rejects
 * non-POST requests, requires an authenticated user with {@code _eChart w}, and
 * validates the document number before this action calls {@link EDocUtil}.</p>
 *
 * @since 2026-06-01
 */
public class NoteBrowserDocumentDelete2Action extends AbstractNoteBrowserDocumentMutation2Action {

    private String delDocumentNo;

    @Override
    protected boolean hasMutationParameter() {
        return delDocumentNo != null && !delDocumentNo.isEmpty();
    }

    @Override
    protected boolean hasValidMutationParameters() {
        return isPositiveInteger(delDocumentNo);
    }

    @Override
    protected String invalidParameterMessage() {
        return "invalid delDocumentNo";
    }

    @Override
    protected void mutateDocument() throws DocumentMutationException {
        deleteDocument(delDocumentNo);
    }

    @Override
    protected boolean handlesMutationException() {
        return true;
    }

    @Override
    protected String logMessage() {
        return "noteBrowser deleteDocument failed docNo=" + LogSafe.sanitize(delDocumentNo);
    }

    protected void deleteDocument(String docNo) throws DocumentMutationException {
        try {
            EDocUtil.deleteDocument(docNo);
        } catch (Exception e) {
            throw new DocumentMutationException(e);
        }
    }

    public String getDelDocumentNo() { return delDocumentNo; }
    @StrutsParameter public void setDelDocumentNo(String v) { this.delDocumentNo = v; }
}
