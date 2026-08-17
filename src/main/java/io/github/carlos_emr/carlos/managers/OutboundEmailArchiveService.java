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

import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveDeletion;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveDto;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import java.io.IOException;

/**
 * Archives exact outbound email artifacts into the patient file and records
 * controlled deletion tombstones for audit.
 *
 * @since 2026-07-07
 */
public interface OutboundEmailArchiveService {

    /**
     * Stores the finalized outbound artifact through eDoc and persists archive metadata.
     *
     * @param loggedInInfo current user context for eDoc permissions and audit logging
     * @param request archive payload and transport metadata
     * @return persisted archive metadata linked to the patient file
     * @throws IllegalArgumentException when the request is incomplete or attachment metadata is invalid
     * @throws IOException when eDoc storage fails
     */
    OutboundEmailArchive archive(LoggedInInfo loggedInInfo, OutboundEmailArchiveDto request) throws IOException;

    /**
     * Marks an archive as deleted and persists a permanent tombstone.
     *
     * <p><b>This is a logical retire, not an erasure, and that is deliberate.</b> An
     * outbound communication archive is evidentiary: a deletion that leaves no trace
     * is indistinguishable from a communication that never happened, which is exactly
     * what the archive exists to disprove. So this method flips {@code deleted}, writes
     * an immutable {@link OutboundEmailArchiveDeletion} tombstone carrying the original
     * SHA-256 and byte size, and stops there. It does <em>not</em> remove the eDoc row,
     * and it does <em>not</em> unlink the stored artifact from {@code DOCUMENT_DIR} —
     * the bytes remain verifiable against the tombstone hash.</p>
     *
     * <p>Consequences a caller must plan for: the archived artifact stays visible in
     * the patient's document browser, and this workflow alone does not satisfy a
     * patient erasure request. Suppressing retired archives from the eDoc view, and any
     * genuine purge path, belong to the archive UI work and must keep the tombstone.</p>
     *
     * <p>Authority note: deletion admits either {@code _admin.edocdelete w} or plain
     * {@code _edoc w}, so today the delete gate is no stricter than the archive gate —
     * anyone who can create an archive can retire one. Tightening that (for example to
     * admin-only, or to the archiving provider) is a policy decision, not an oversight.</p>
     *
     * @param loggedInInfo deleting user context
     * @param archiveId archive metadata identifier
     * @param deleteReason required deletion reason
     * @return immutable deletion tombstone
     * @throws IllegalArgumentException when the archive identifier or deletion reason is missing
     * @throws IllegalStateException when deletion is blocked by legal hold or the archive is already deleted
     * @throws SecurityException when the caller lacks delete authority or patient-record access
     */
    OutboundEmailArchiveDeletion recordControlledDeletion(LoggedInInfo loggedInInfo, Integer archiveId, String deleteReason);
}
