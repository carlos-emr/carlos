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

/**
 * Owns both authenticated scans for a complete current/revision inventory. It emits no clinical
 * content. A chart of any number of notes is inventoried: no count and no size estimate refuses
 * or holds one, and neither a note's receipt nor its metadata row is kept: each receipt is
 * reduced as it is read to the note id and one digest of the compared cells
 * ({@link CdsExportNoteRevisionPlanner.Witnesses}, 40 bytes a note), and each metadata row is
 * compared and placed as it is read. What remains for each note is that witness, two bits and
 * its place in the result.
 */
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
        // maximumInventoryBytes is the caller's former bound: not applied and not checked.
        if (!source.capture().tables().keySet().equals(requiredTables)
                || !requiredTables.contains("casemgmt_note")) invalid();
        var scope = source.capture().tables().get("casemgmt_note");
        var witnesses = new CdsExportNoteRevisionPlanner.Witnesses();
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
                            // Reduced here; the receipt itself is not kept.
                            witnesses.add(row);
                        });
        if (!scanned.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || witnesses.size() != scope.rows()
                || !Objects.equals(scanned.rows().get("casemgmt_note"), scope.rows())
                || scanned.unavailableTables().contains("casemgmt_note")) invalid();
        // A note with two witnesses, or a witnessed note outside the authorized scopes, is
        // refused when the inventory opens.
        var inventory =
                new CdsExportNoteRevisionPlanner()
                        .open(
                                patient.patient(),
                                source.manifest().plaintextSha256(),
                                scope.noteAccess(),
                                witnesses,
                                control::checkpoint);
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
                                inventory::accept);
        if (!actual.equals(source.capture())) invalid();
        var result = inventory.finish();
        captures.authorizeCapturedSources(lease, patient, requiredTables, actual, control);
        patient.require(lease);
        control.checkpoint();
        return result;
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NOTE_REVISION_INVENTORY_INVALID");
    }
}
