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

/**
 * Versioned, PHI-free observation; authorization remains mandatory before returning it.
 *
 * <p>Resume evidence, counts and a time only: {@code keptCharts} is the most finished charts any
 * one stopped attempt of the job keeps, encrypted, for a resumed attempt to adopt (normally one
 * attempt keeps them), and {@code keptUntil} the earliest time a kept attempt becomes due for
 * erasure; both are lower bounds, 0 and null when nothing is kept. {@code adoptedCharts} and
 * {@code builtCharts} are what the job's latest attempt recorded in its checkpoint: charts adopted
 * unchanged from a stopped attempt and charts built from a new capture. They follow the attempt's
 * checkpoint, recorded at each lease heartbeat, and are final when the attempt ends.
 */
public record CdsExportJobDto(String id, CdsExportState state, long revision, Long totalPatients,
        long processedPatients, long blockedPatients, boolean cancelRequested, boolean workerOwned,
        long epoch, String safeCode, Instant createdAt, Instant updatedAt, boolean artifactAvailable,
        long keptCharts, Instant keptUntil, long adoptedCharts, long builtCharts) {
    public CdsExportJobDto {
        if (keptCharts < 0 || adoptedCharts < 0 || builtCharts < 0 || (keptCharts == 0) != (keptUntil == null))
            throw new IllegalArgumentException("CDS_EXPORT_STATUS_INVALID");
    }
    /** An observation without resume evidence: nothing kept, adopted or built. */
    public CdsExportJobDto(String id, CdsExportState state, long revision, Long totalPatients,
            long processedPatients, long blockedPatients, boolean cancelRequested, boolean workerOwned,
            long epoch, String safeCode, Instant createdAt, Instant updatedAt, boolean artifactAvailable) {
        this(id, state, revision, totalPatients, processedPatients, blockedPatients, cancelRequested, workerOwned,
                epoch, safeCode, createdAt, updatedAt, artifactAvailable, 0, null, 0, 0);
    }
    /** A stopped/expired attempt can be waiting even if its last durable phase was capture. */
    public boolean waiting() { return !state.terminal() && !workerOwned; }
    /** A READY label cannot compensate for unknown/unaccounted patients or a cancellation. */
    public boolean complete() {
        return state == CdsExportState.READY && artifactAvailable && !cancelRequested && totalPatients != null
                && totalPatients > 0 && processedPatients == totalPatients && blockedPatients == 0;
    }
    /**
     * A downloadable package that deliberately leaves out the held patients it lists. It is never
     * a complete migration: complete() stays false and callers must surface the held count.
     */
    public boolean heldBack() {
        return state == CdsExportState.READY && artifactAvailable && !cancelRequested && totalPatients != null
                && totalPatients > 0 && processedPatients == totalPatients && blockedPatients > 0
                && blockedPatients < totalPatients;
    }
}
