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
import java.io.IOException;
import java.util.*;

/** Metadata-only observation membership, independent of serialized values or mapper receipts. */
final class CdsExportNativeCarePlanner {
    record Occurrence(
            String table,
            String id,
            String element,
            CdsExportCapturedTextRowDto observation,
            List<CdsExportCapturedTextRowDto> extensions,
            List<CdsExportCapturedTextRowDto> definitions) {
        Occurrence {
            extensions = List.copyOf(extensions);
            definitions = List.copyOf(definitions);
        }

        /**
         * A measurementsDeleted row: a clinician's deletion or an HL7 version superseded by a
         * newer one. It is accounted field by field and never exported (see
         * CdsExportNativeCareMapper.retired).
         */
        boolean deleted() {
            return table.equals("measurementsDeleted");
        }

        @Override
        public String toString() {
            return "CdsExportNativeCarePlanner.Occurrence[protected]";
        }
    }

    List<Occurrence> plan(
            CdsExportRetainedIndexDto index,
            Map<String, CdsExportCapturedTextRowDto> current,
            Runnable checkpoint)
            throws IOException {
        var imported = new HashSet<String>();
        for (String address : index.appliedUnits())
            for (var ref : index.references().getOrDefault(address, List.of()))
                if (ref.table().equals("measurements")) imported.add(ref.id());
        var result = new ArrayList<Occurrence>();
        for (var row : current.values()) {
            checkpoint.run();
            boolean deleted = row.table().equals("measurementsDeleted");
            if (!deleted
                    && (!row.table().equals("measurements")
                            || imported.contains(row.fields().get("id")))) continue;
            String id = row.fields().get("id");
            // Deletion leaves the extensions keyed by the removed observation's original id.
            String owner = deleted ? row.fields().get("originalId") : id;
            var extensions = new ArrayList<CdsExportCapturedTextRowDto>();
            var definitions = new ArrayList<CdsExportCapturedTextRowDto>();
            for (var other : current.values()) {
                if (other.table().equals("measurementsExt")
                        && owner != null
                        && owner.equals(other.fields().get("measurement_id"))) extensions.add(other);
                // The vocabulary is captured for current observations and accounted by them; a
                // deleted version is never exported, so it borrows no dictionary row.
                if (!deleted
                        && other.table().equals("measurementType")
                        && Objects.equals(row.fields().get("type"), other.fields().get("type"))
                        && Objects.equals(
                                row.fields().get("measuringInstruction"),
                                other.fields().get("measuringInstruction"))) definitions.add(other);
            }
            result.add(
                    new Occurrence(
                            row.table(),
                            id,
                            deleted ? null : standardElement(row, extensions),
                            row,
                            extensions,
                            definitions));
        }
        result.sort(Comparator.comparing(Occurrence::table).thenComparing(Occurrence::id));
        return List.copyOf(result);
    }

    /**
     * The mapper's own exact-representation decision, so the planned and the mapped CareElements
     * can never disagree. Ambiguous extensions plan no standard element; the mapper then refuses
     * them explicitly.
     */
    private static String standardElement(
            CdsExportCapturedTextRowDto row, List<CdsExportCapturedTextRowDto> extensions) {
        var extra = new HashMap<String, String>();
        for (var ext : extensions) {
            String key = ext.fields().get("keyval");
            if (key == null || extra.containsKey(key)) return null;
            extra.put(key, ext.fields().get("val"));
        }
        var standard = CdsExportNativeCareMapper.standard(row.fields(), extra);
        return standard == null ? null : standard.element();
    }
}
