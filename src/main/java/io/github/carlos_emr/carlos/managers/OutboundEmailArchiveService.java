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
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveLegalHoldEvent;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveDto;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import java.io.IOException;

/**
 * Archives exact outbound email artifacts into the patient file and records
 * controlled deletion tombstones for audit.
 *
 * @since 2026-08-14
 */
public interface OutboundEmailArchiveService {

    /**
     * Stores the finalized outbound artifact through eDoc and persists archive metadata.
     *
     * <p>Requires {@code _edoc w} plus access to the email log's patient record. Unlike the
     * three privileged operations below, this is the archive-creation gate, not the
     * admin gate.</p>
     *
     * @param loggedInInfo current user context for eDoc permissions and audit logging
     * @param request archive payload and transport metadata
     * @return persisted archive metadata linked to the patient file
     * @throws IllegalArgumentException when the request is incomplete, the referenced email log
     *         does not exist, or attachment metadata is invalid
     * @throws SecurityException when the caller lacks {@code _edoc w}, lacks access to the
     *         patient record, or supplies an attachment document belonging to another patient
     * @throws IOException when eDoc storage fails
     */
    OutboundEmailArchive archive(LoggedInInfo loggedInInfo, OutboundEmailArchiveDto request) throws IOException;

    /**
     * Advances an archive's send lifecycle to record what the transport actually did.
     *
     * <p>An archive is created before dispatch, so on its own it only proves the message was
     * captured. Without these transitions every row stays at {@code ARCHIVED} forever, making an
     * email that was accepted for delivery indistinguishable from one that was refused and one
     * that was never dispatched at all — in a record kept under permanent legal hold.</p>
     *
     * <p>Callers treat this as best-effort bookkeeping: a failure here must never change the
     * caller's send outcome, because the message may already be in the recipient's hands. An
     * outcome that could not be recorded leaves the row at its previous state, which is why
     * neither {@code ARCHIVED} nor {@code SEND_ATTEMPTED} may be read as "delivery failed".</p>
     *
     * @param loggedInInfo current user context for audit logging
     * @param archiveId persisted archive identifier; null means archiving never produced a row,
     *        so there is no lifecycle to advance and the call is a no-op
     * @param outcome lifecycle transition to apply
     * @return the updated archive, or {@code null} when {@code archiveId} is null
     * @throws IllegalArgumentException when no archive matches, the outcome is null, or the
     *         caller carries no provider number
     * @throws SecurityException when the caller lacks {@code _edoc w} or access to the
     *         archive's patient record
     */
    OutboundEmailArchive recordSendOutcome(LoggedInInfo loggedInInfo, Integer archiveId, SendOutcome outcome);

    /**
     * Send lifecycle transitions available to {@link #recordSendOutcome}.
     *
     * <p>Deliberately narrower than the status constants on the model: transports report that
     * they tried, that custody was accepted, or that they failed. Nothing available to CARLOS
     * at send time can report delivery to the recipient, so no such transition exists.</p>
     */
    enum SendOutcome {
        /** Handed to the transport; result not yet known. */
        ATTEMPTED,
        /** Transport accepted custody. Not proof of delivery. */
        ACCEPTED,
        /** Transport refused the artifact, or failed before taking it. */
        FAILED
    }

    /**
     * Returns an archive's metadata for an authorized caller, refusing deleted archives.
     *
     * <p>Metadata only. The stored artifact is read through
     * {@link #readArchivedArtifact(LoggedInInfo, Integer)}, which additionally verifies the
     * bytes against the recorded size and hash.</p>
     *
     * <p>Access is audited: a successful read records who looked at which archive, because the
     * archive holds retained patient email. Audit persistence must succeed before data returns.</p>
     *
     * @param loggedInInfo current user context
     * @param archiveId persisted archive identifier
     * @return the archive, with its demographic and document hydrated
     * @throws IllegalArgumentException when the identifier is null or names no archive
     * @throws IllegalStateException when the archive, or the eDoc behind it, has been deleted
     * @throws SecurityException when the caller lacks {@code _edoc r} or access to the patient
     * @since 2026-08-19
     */
    OutboundEmailArchive getActiveArchive(LoggedInInfo loggedInInfo, Integer archiveId);

    /**
     * Reads a stored archive artifact, verifying it still matches what was archived.
     *
     * <p>The archive is the record of what was actually sent to a patient, so bytes that no
     * longer match the recorded size and SHA-256 are treated as a security event rather than a
     * read error: the mismatch is audited before the failure propagates. Callers get an
     * {@link IOException} and no bytes, never partially-verified content.</p>
     *
     * <p>The row is read under a write lock so the archive cannot transition to its logically
     * deleted state between authorization and completion of the read. Controlled deletion
     * deliberately retains the stored bytes.</p>
     *
     * @param loggedInInfo current user context
     * @param archiveId persisted archive identifier
     * @return the verified artifact bytes
     * @throws IllegalArgumentException when the identifier is null or names no archive
     * @throws IllegalStateException when the archive, or the eDoc behind it, has been deleted
     * @throws SecurityException when the caller lacks {@code _edoc r} or access to the patient
     * @throws IOException when metadata is missing, the file is absent or unreadable, or the
     *         bytes do not match the recorded size or hash
     * @since 2026-08-19
     */
    byte[] readArchivedArtifact(LoggedInInfo loggedInInfo, Integer archiveId) throws IOException;

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
     * <p>Consequences a caller must plan for: the archived artifact is suppressed from the
     * ordinary eDoc views but its row and bytes remain retained, so this workflow alone does
     * not satisfy a patient erasure request. Any genuine purge path must preserve the
     * tombstone.</p>
     *
     * <p>Requires {@code _admin.edocdelete w}. Plain {@code _edoc w} is deliberately
     * not sufficient: that is the same right needed to create an archive, so accepting
     * it would make the delete gate no stricter than the archive gate.</p>
     *
     * <p>Every archive is created under legal hold, so this call fails until an admin
     * has released the hold through {@link #releaseLegalHold}. That release is recorded
     * separately, because the provider who authorises a deletion and the provider who
     * performs it are frequently different people.</p>
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

    /**
     * Releases the legal hold on an archive, making it eligible for controlled deletion.
     *
     * <p>Legal hold is on from creation, so this is the gate that actually protects the
     * archive — it requires {@code _admin.edocdelete w} and writes an immutable
     * {@link OutboundEmailArchiveLegalHoldEvent} naming the releasing provider, the
     * reason, and the timestamp. Releasing does not delete anything.</p>
     *
     * @param loggedInInfo releasing user context
     * @param archiveId archive metadata identifier
     * @param reason required justification retained for audit
     * @return immutable legal hold event
     * @throws IllegalArgumentException when the archive identifier or reason is missing
     * @throws IllegalStateException when no hold is active or the archive is already deleted
     * @throws SecurityException when the caller lacks admin delete authority or patient-record access
     */
    OutboundEmailArchiveLegalHoldEvent releaseLegalHold(LoggedInInfo loggedInInfo, Integer archiveId, String reason);

    /**
     * Re-applies the legal hold on an archive, blocking controlled deletion again.
     *
     * @param loggedInInfo user context placing the hold
     * @param archiveId archive metadata identifier
     * @param reason required justification retained for audit
     * @return immutable legal hold event
     * @throws IllegalArgumentException when the archive identifier or reason is missing
     * @throws IllegalStateException when a hold is already active or the archive is already deleted
     * @throws SecurityException when the caller lacks admin delete authority or patient-record access
     */
    OutboundEmailArchiveLegalHoldEvent placeLegalHold(LoggedInInfo loggedInInfo, Integer archiveId, String reason);
}
