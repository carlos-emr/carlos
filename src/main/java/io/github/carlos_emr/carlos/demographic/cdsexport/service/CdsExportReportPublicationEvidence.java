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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsReportNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Exact publication/control references within an already authenticated, unchanged original report receipt. */
final class CdsExportReportPublicationEvidence {
    static void require(String address, CdsExportRetainedIndexDto index, List<CdsExportRetainedIndexDto.Reference> refs,
            Map<String, CdsReportNativeDto.Variant> variants, Runnable checkpoint) throws IOException {
        checkpoint.run(); if (address == null || !address.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}:[0-9a-f]{64}")
                || index == null || !index.appliedUnits().contains(address) || refs == null || variants == null
                || variants.isEmpty() || variants.size() > 2 || refs.size() != variants.size() * 4) invalid();
        String run = address.substring(0, 36), unit = address.substring(37); var files = new HashMap<String, CdsExportCapturedTextRowDto>();
        var links = new HashMap<String, CdsExportCapturedTextRowDto>(); var counts = new HashMap<String, Integer>(); var distinct = new HashSet<String>();
        for (var ref : refs) {
            checkpoint.run();
            if (ref == null || !Set.of("document", "ctl_document", "cds_import_file", "cds_import_file_reference").contains(ref.table())
                    || !distinct.add(ref.table() + ":" + ref.id())) invalid(); counts.merge(ref.table(), 1, Integer::sum);
            if (ref.table().equals("cds_import_file") || ref.table().equals("cds_import_file_reference")) {
                var row = index.tables().getOrDefault(ref.table(), Map.of()).get(ref.id());
                if (row == null || !ref.id().equals(row.recordKey()) || !ref.id().equals(row.fields().get("id"))) invalid();
                if (ref.table().equals("cds_import_file")) files.put(ref.id(), row); else links.put(ref.id(), row);
            }
        }
        if (counts.size() != 4 || counts.values().stream().anyMatch(count -> count != variants.size())) invalid();
        var used = new HashSet<String>(); var usedLinks = new HashSet<String>();
        for (var entry : variants.entrySet()) {
            checkpoint.run(); var variant = entry.getValue();
            if (variant == null || !entry.getKey().equals(variant.name()) || !Set.of("embedded", "referenced").contains(entry.getKey())) invalid();
            String filename = "cds-" + unit + "-" + variant.name() + "-" + variant.digest() + "." + variant.extension();
            var matching = new ArrayList<CdsExportCapturedTextRowDto>();
            for (var file : files.values()) {
                String location = file.fields().get("location"); if (location == null) invalid(); Path path;
                try { path = Path.of(location); } catch (RuntimeException malformed) { throw new IOException("CDS_EXPORT_REPORT_PUBLICATION_EVIDENCE_INVALID"); }
                if (!path.isAbsolute() || !path.equals(path.normalize()) || path.getFileName() == null) invalid();
                if (filename.equals(path.getFileName().toString())) matching.add(file);
            }
            if (matching.size() != 1) invalid(); var file = matching.getFirst(); var fields = file.fields(); String fileId = fields.get("id");
            if (!used.add(fileId) || !fileId.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                    || !"PUBLISHED".equals(fields.get("state")) || !"1".equals(fields.get("generation"))
                    || !variant.digest().equals(fields.get("digest")) || !Long.toString(variant.length()).equals(fields.get("byte_length"))
                    || !CdsImportKeys.hash(fields.get("location")).equals(fields.get("location_key"))) invalid();
            String linkId = CdsImportKeys.hash(run, unit, fileId); var link = links.get(linkId);
            if (link == null || !usedLinks.add(linkId) || !run.equals(link.fields().get("run_id"))
                    || !unit.equals(link.fields().get("unit_key")) || !fileId.equals(link.fields().get("file_id"))) invalid();
        }
        if (!used.equals(files.keySet()) || !usedLinks.equals(links.keySet())) invalid(); checkpoint.run();
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_REPORT_PUBLICATION_EVIDENCE_INVALID"); }
}
