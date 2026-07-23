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

package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import java.io.IOException;

/**
 * Narrow capability for persisting an outbound email archive artifact as a patient eDoc.
 *
 * <p>This deliberately sits <strong>outside</strong> the public {@link DocumentManager} API so the
 * {@code _edoc}-write bypass it grants is not a general, reusable eDoc-write surface. Unlike
 * {@link DocumentManager#createDocument}, the write is <em>not</em> gated on the caller's
 * {@code _edoc} write privilege, because the outbound email archive is a mandatory compliance
 * record written as a side-effect of an already-authorized email send. Authorization is enforced
 * by the sole caller, {@link OutboundEmailArchiveService}, on the send privilege
 * ({@code _email} write) so that authorized senders without chart-write rights (e.g. front-desk
 * staff) still have their outbound email archived. The document is attributed to the acting
 * provider via {@code doccreator} for audit.</p>
 *
 * <p>Implemented only by {@code DocumentManagerImpl} and injected only into the archive service;
 * do not widen its use without re-evaluating the authorization model.</p>
 *
 * @since 2026-07-23
 */
public interface OutboundEmailArchiveDocumentPersister {

    /**
     * Persists an outbound email archive document without enforcing the caller's {@code _edoc}
     * write privilege. The caller MUST have authorized the initiating send (see interface docs).
     *
     * @param loggedInInfo  acting user, recorded as the document creator for audit
     * @param document      document to create
     * @param demographicNo demographic the document is filed to
     * @param providerNo    optional provider to route the document to
     * @param documentData  document byte data
     * @return the created document record
     * @throws IOException if writing the document data fails
     */
    Document persistArchiveDocument(LoggedInInfo loggedInInfo, Document document, Integer demographicNo, String providerNo, byte[] documentData) throws IOException;
}
