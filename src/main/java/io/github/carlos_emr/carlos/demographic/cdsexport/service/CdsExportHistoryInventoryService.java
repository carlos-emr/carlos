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

/** Independent history membership from complete row witnesses, never from mapper output. */
@Service
public class CdsExportHistoryInventoryService {
    private final CdsExportNativeCaptureService captures;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportNoteRevisionInventoryService revisions;

    public CdsExportHistoryInventoryService(
            CdsExportNativeCaptureService captures,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNoteRevisionInventoryService revisions) {
        this.captures = Objects.requireNonNull(captures);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.revisions = Objects.requireNonNull(revisions);
    }

    public List<CdsExportHistoryOccurrenceService.Occurrence> plan(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportRetainedIndexDto index,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        var inventory =
                revisions.inventory(
                        lease,
                        patient,
                        tables,
                        source,
                        bounds.fields(),
                        bounds.bytes(),
                        control);
        var context =
                CdsExportHistoryCurrentContext.read(
                        captures, witnesses, lease, patient, tables, source, bounds, control);
        return CdsExportHistoryOccurrenceService.plan(
                index, inventory, context, control::checkpoint);
    }

    /**
     * The chart's system markers that file no note ({@link
     * CdsExportHistoryOccurrenceService#markers}), each with its patient issue and catalogue
     * rows checked against their whole witnesses, in id order. A chart read in windows loads
     * them window by window and keeps only these rows.
     */
    public List<CdsExportIssueMarkerMapper.Marker> markers(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        var context =
                CdsExportHistoryCurrentContext.read(
                        captures, witnesses, lease, patient, tables, source, bounds, control);
        var ids = CdsExportHistoryOccurrenceService.markers(context, control::checkpoint);
        var result = new ArrayList<CdsExportIssueMarkerMapper.Marker>();
        if (!context.windowed()) {
            for (String id : ids) result.add(marker(context, id));
            return List.copyOf(result);
        }
        var sizes = new CdsExportSectionWindowPlanner.Sizes(bounds.window());
        for (int from = 0; from < ids.size(); ) {
            int start = from;
            var window =
                    CdsExportSectionWindowPlanner.loadNext(
                            sizes,
                            ids.size() - start,
                            (length, budget) ->
                                    context.window(
                                            Set.of(),
                                            Set.copyOf(ids.subList(start, start + length)),
                                            List.of(),
                                            budget));
            for (String id : ids.subList(start, start + window.length()))
                result.add(marker(window.value(), id));
            from = start + window.length();
        }
        return List.copyOf(result);
    }

    private static CdsExportIssueMarkerMapper.Marker marker(
            CdsExportHistoryCurrentContext rows, String id) throws IOException {
        var issue = rows.original("casemgmt_issue", id);
        if (issue == null) throw new IOException("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
        var definition = rows.original("issue", issue.value().fields().get("issue_id"));
        if (definition == null)
            throw new IOException("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
        return new CdsExportIssueMarkerMapper.Marker(issue, definition);
    }
}
