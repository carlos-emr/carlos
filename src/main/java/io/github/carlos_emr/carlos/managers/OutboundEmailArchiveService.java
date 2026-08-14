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
     * Loads active archive metadata for view/download workflows after enforcing patient and eDoc read access.
     *
     * @param loggedInInfo current user context for permissions and audit logging
     * @param archiveId archive metadata identifier
     * @return active archive metadata
     * @throws IllegalArgumentException when the archive identifier is missing or not found
     * @throws IllegalStateException when the archive has been controlled-deleted
     * @throws SecurityException when the caller lacks eDoc or patient-record read access
     */
    OutboundEmailArchive getActiveArchive(LoggedInInfo loggedInInfo, Integer archiveId);

    /**
     * Reads the archived outbound artifact bytes from eDoc storage after enforcing access checks.
     *
     * @param loggedInInfo current user context for permissions and audit logging
     * @param archiveId archive metadata identifier
     * @return archived artifact bytes
     * @throws IOException when the archived eDoc file cannot be read, required integrity metadata
     *         is missing or invalid, or the stored bytes do not match that metadata
     * @throws IllegalStateException when the archive or its backing eDoc has been controlled-deleted
     * @throws SecurityException when the caller lacks eDoc or patient-record read access
     */
    byte[] readArchivedArtifact(LoggedInInfo loggedInInfo, Integer archiveId) throws IOException;

    /**
     * Marks an archive as deleted and persists a permanent tombstone.
     *
     * @param loggedInInfo deleting user context
     * @param archiveId archive metadata identifier
     * @param deleteReason required deletion reason
     * @return immutable deletion tombstone
     * @throws IllegalArgumentException when the archive identifier or deletion reason is missing
     * @throws IllegalStateException when deletion is blocked by legal hold or the archive is already deleted
     * @throws SecurityException when the caller lacks deletion authority or patient-record access
     */
    OutboundEmailArchiveDeletion recordControlledDeletion(LoggedInInfo loggedInInfo, Integer archiveId, String deleteReason);
}
