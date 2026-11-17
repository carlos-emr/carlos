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
package io.github.carlos_emr.carlos.demographic.cdsimport.dto;

/**
 * Protected owner-only upload receipt; contains no server filesystem location. runId identifies only
 * actual execution, and dryRunId identifies only the separate preview acceptance. {@code removedAt}
 * (epoch milliseconds) is when an upload that is still unsubmitted is abandoned and its bytes
 * removed, the end of its idle window; it is null once the upload is submitted or retired.
 */
public record CdsUploadDto(String id, String fileName, long expectedBytes, long receivedBytes,
        long chunkBytes, long committedChunks, String state, String manifestSha256, String submissionKey,
        String runId, String dryRunId, long updatedAt, Long removedAt) {
    @Override public String toString() { return "CdsUploadDto[protected]"; }
}
