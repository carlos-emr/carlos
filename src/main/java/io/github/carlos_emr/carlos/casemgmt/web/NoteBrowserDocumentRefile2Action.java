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
 * Refiles a note-browser document to a validated destination queue.
 *
 * <p>The shared {@link AbstractNoteBrowserDocumentMutation2Action} contract rejects
 * non-POST requests, requires an authenticated user with {@code _eChart w}, and
 * validates both the document number and {@code queueId} before this action calls
 * {@link EDocUtil}. Refile failures are logged with safe values and returned to
 * the Struts result as a generic mutation error.</p>
 *
 * @since 2026-06-01
 */
public class NoteBrowserDocumentRefile2Action extends AbstractNoteBrowserDocumentMutation2Action {

    private String refileDocumentNo;
    private String queueId;

    @Override
    protected boolean hasMutationParameter() {
        return refileDocumentNo != null && !refileDocumentNo.isEmpty();
    }

    @Override
    protected boolean hasValidMutationParameters() {
        return isPositiveInteger(refileDocumentNo) && isPositiveInteger(queueId);
    }

    @Override
    protected String invalidParameterMessage() {
        return "invalid refileDocumentNo or queueId";
    }

    @Override
    protected void mutateDocument() throws DocumentMutationException {
        refileDocument(refileDocumentNo, queueId);
    }

    @Override
    protected boolean handlesMutationException() {
        return true;
    }

    @Override
    protected String logMessage() {
        return "noteBrowser refileDocument failed docNo=" + LogSafe.sanitize(refileDocumentNo)
                + " queueId=" + LogSafe.sanitize(queueId);
    }

    protected void refileDocument(String docNo, String queue) throws DocumentMutationException {
        try {
            EDocUtil.refileDocument(docNo, queue);
        } catch (Exception e) {
            throw new DocumentMutationException(e);
        }
    }

    public String getRefileDocumentNo() { return refileDocumentNo; }
    @StrutsParameter public void setRefileDocumentNo(String v) { this.refileDocumentNo = v; }
    public String getQueueId() { return queueId; }
    @StrutsParameter public void setQueueId(String v) { this.queueId = v; }
}
