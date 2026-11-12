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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import java.time.Instant;

/** Versioned, PHI-free observation; authorization remains mandatory before returning it. */
public record CdsExportJobDto(String id, CdsExportState state, long revision, Long totalPatients,
        long processedPatients, long blockedPatients, boolean cancelRequested, boolean workerOwned,
        long epoch, String safeCode, Instant createdAt, Instant updatedAt, boolean artifactAvailable) {
    /** A stopped/expired attempt can be waiting even if its last durable phase was capture. */
    public boolean waiting() { return !state.terminal() && !workerOwned; }
    /** A READY label cannot compensate for unknown/unaccounted patients or a cancellation. */
    public boolean complete() {
        return state == CdsExportState.READY && artifactAvailable && !cancelRequested && totalPatients != null
                && totalPatients > 0 && processedPatients == totalPatients && blockedPatients == 0;
    }
}
