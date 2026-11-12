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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Service;

/** Owns both authenticated scans for a complete, bounded current/revision inventory. It emits no clinical content. */
@Service
public class CdsExportNoteRevisionInventoryService {
    private final CdsExportNativeCaptureService captures;
    private final CdsExportNativeRowReceiptReader receipts;

    public CdsExportNoteRevisionInventoryService(
            CdsExportNativeCaptureService captures, CdsExportNativeRowReceiptReader receipts) {
        this.captures = Objects.requireNonNull(captures);
        this.receipts = Objects.requireNonNull(receipts);
    }

    /** Neither an omitted revision nor an unsupported UUID may silently choose a current clinical note. */
    public CdsExportNoteRevisionPlanner.Result inventory(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            long maximumFields,
            long maximumBytes,
            long maximumInventoryBytes,
            CdsExportRunControl control)
            throws IOException {
        Objects.requireNonNull(source);
        Objects.requireNonNull(control);
        patient.require(lease);
        control.checkpoint();
        if (maximumInventoryBytes < 1
                || maximumInventoryBytes > 268_435_456L
                || !source.capture().tables().keySet().equals(requiredTables)
                || !requiredTables.contains("casemgmt_note")) invalid();
        var scope = source.capture().tables().get("casemgmt_note");
        if (scope.rows() > 8192) limit();
        var witnesses = new HashMap<Long, CdsExportNativeRowReceiptDto>();
        var metadata = new ArrayList<CdsExportNoteRevisionDto>();
        long[] memory = {0};
        var scanned =
                receipts.scan(
                        lease,
                        patient,
                        requiredTables,
                        source,
                        maximumFields,
                        maximumBytes,
                        control,
                        row -> {
                            if (!row.table().equals("casemgmt_note")) return;
                            control.checkpoint();
                            long id;
                            try {
                                id = Long.parseLong(row.id());
                            } catch (NumberFormatException malformed) {
                                invalid();
                                return;
                            }
                            if (id < 1
                                    || !Long.toString(id).equals(row.id())
                                    || row.state() == null
                                    || row.partial()
                                    || row.digest() == null
                                    || witnesses.size() >= 8192
                                    || witnesses.putIfAbsent(id, row) != null) invalid();
                            memory[0] =
                                    Math.addExact(
                                            memory[0], 4096L + row.state().columns().size() * 512L);
                            if (memory[0] > maximumInventoryBytes) limit();
                        });
        if (!scanned.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || witnesses.size() != scope.rows()
                || !Objects.equals(scanned.rows().get("casemgmt_note"), scope.rows())
                || scanned.unavailableTables().contains("casemgmt_note")) invalid();
        var actual =
                new CdsExportNoteRevisionReader(captures)
                        .forEach(
                                lease,
                                patient,
                                requiredTables,
                                source,
                                maximumFields,
                                maximumBytes,
                                control,
                                row -> {
                                    if (metadata.size() >= 8192) limit();
                                    metadata.add(row);
                                    memory[0] = Math.addExact(memory[0], 2048L);
                                    if (memory[0] > maximumInventoryBytes) limit();
                                });
        if (!actual.equals(source.capture())) invalid();
        var result =
                new CdsExportNoteRevisionPlanner()
                        .plan(
                                patient.patient(),
                                source.manifest().plaintextSha256(),
                                scope.noteAccess(),
                                metadata,
                                witnesses,
                                control::checkpoint);
        captures.authorizeCapturedSources(lease, patient, requiredTables, actual, control);
        patient.require(lease);
        control.checkpoint();
        return result;
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
    }

    private static void limit() {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                .CdsExportReviewException(
                io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException
                        .Reason.RESOURCE_LIMIT);
    }
}
