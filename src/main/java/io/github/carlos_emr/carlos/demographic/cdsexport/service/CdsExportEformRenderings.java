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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The captured {@value CdsExportEformRenderingSourceReader#TABLE} rows of one patient, read back
 * from the sealed capture: each current eForm's render outcome, its saving provider's name and,
 * for a rendered form, the descriptor of its PDF in the authenticated values member. The PDF
 * itself is never read here; the emission copies it straight from the capture into the attachment.
 *
 * @since 2026-11-13
 */
final class CdsExportEformRenderings {
    private static final Set<String> TEXT =
            Set.of("author_first_name", "author_last_name", "fdid", "outcome", "reason");

    /** One captured outcome; {@code content} is the PDF descriptor, or null when unavailable. */
    record Rendering(
            String fdid,
            CdsExportEformRenderService.Reason reason,
            String authorFirstName,
            String authorLastName,
            CdsExportCapturedFieldDto content,
            CdsExportCapturedTextRowDto row) {
        boolean rendered() {
            return content != null;
        }

        @Override
        public String toString() {
            return "CdsExportEformRenderings.Rendering[protected]";
        }
    }

    private CdsExportEformRenderings() {}

    /** True when the capture holds the renderings (Reports selected after this table existed). */
    static boolean captured(Set<String> tables) {
        return tables.contains(CdsExportEformRenderingSourceReader.TABLE);
    }

    /** The patient's renderings by eForm identity ({@code fdid} text), in identity order. */
    static Map<String, Rendering> read(
            CdsExportNativeCaptureService captures,
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        var result = new TreeMap<String, Rendering>();
        if (!captured(tables)) return result;
        var sink = new Fields(bounds.nativeRows(), result);
        var actual =
                captures.scan(
                        lease,
                        patient,
                        tables,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        control,
                        sink);
        sink.finish();
        var scope = actual.tables().get(CdsExportEformRenderingSourceReader.TABLE);
        if (!actual.equals(source.capture()) || scope == null || result.size() != scope.rows())
            invalid();
        return result;
    }

    static final class Fields implements CdsExportNativeCaptureReader.Fields {
        private final int maximum;
        private final Map<String, Rendering> result;
        private final Map<String, String> text = new HashMap<>();
        private final Map<String, CdsExportCapturedFieldDto> seen = new HashMap<>();
        private String key;
        private CdsExportCapturedFieldDto pending;
        private ByteArrayOutputStream value;

        Fields(int maximum, Map<String, Rendering> result) {
            this.maximum = maximum;
            this.result = result;
        }

        @Override
        public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
            if (!CdsExportEformRenderingSourceReader.TABLE.equals(field.table()))
                return OutputStream.nullOutputStream();
            if (pending != null) invalid();
            if (!field.recordKey().equals(key)) {
                finish();
                key = field.recordKey();
            }
            if (seen.containsKey(field.column()) || field.exactNumeric() != null) invalid();
            pending = field;
            if (field.column().equals("content")) {
                if (field.kind() != CdsExportCapturedFieldDto.Kind.BINARY) invalid();
                return OutputStream.nullOutputStream();
            }
            if (!TEXT.contains(field.column()) || field.bytes() > 4096) invalid();
            if (field.kind() == CdsExportCapturedFieldDto.Kind.SQL_NULL) return OutputStream.nullOutputStream();
            if (field.kind() != CdsExportCapturedFieldDto.Kind.UTF8) invalid();
            value = new ByteArrayOutputStream((int) field.bytes());
            return value;
        }

        @Override
        public void verified(CdsExportCapturedFieldDto field) throws IOException {
            if (!CdsExportEformRenderingSourceReader.TABLE.equals(field.table())) return;
            if (pending != field || seen.putIfAbsent(field.column(), field) != null) invalid();
            if (value != null) {
                if (value.size() != field.bytes()) invalid();
                text.put(
                        field.column(),
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(java.nio.ByteBuffer.wrap(value.toByteArray()))
                                .toString());
                value = null;
            }
            pending = null;
        }

        void finish() throws IOException {
            if (pending != null) invalid();
            if (key == null) return;
            var columns = new java.util.HashSet<>(TEXT);
            columns.add("content");
            if (!seen.keySet().equals(columns)) invalid();
            String fdid = text.get("fdid"), outcome = text.get("outcome"), code = text.get("reason");
            if (fdid == null
                    || !fdid.matches("[1-9][0-9]{0,9}")
                    || !CdsExportPatientTableSourceReader.key(Long.parseLong(fdid)).equals(key)
                    || outcome == null
                    || code == null) invalid();
            var content = seen.get("content");
            CdsExportEformRenderService.Reason reason = null;
            if (CdsExportEformRenderingSourceReader.RENDERED.equals(outcome)) {
                if (!code.isEmpty() || content.bytes() < 5) invalid();
            } else if (CdsExportEformRenderingSourceReader.UNAVAILABLE.equals(outcome)) {
                if (content.bytes() != 0) invalid();
                try {
                    reason = CdsExportEformRenderService.Reason.valueOf(code);
                } catch (IllegalArgumentException unknown) {
                    invalid();
                }
                content = null;
            } else invalid();
            var fields = new TreeMap<String, String>();
            for (String column : TEXT) fields.put(column, text.get(column));
            var descriptor = seen.get("content");
            fields.put("content", descriptor.bytes() == 0 ? "" : "sha256:" + descriptor.sha256());
            if (result.size() >= maximum
                    || result.putIfAbsent(
                                    fdid,
                                    new Rendering(
                                            fdid,
                                            reason,
                                            text.get("author_first_name"),
                                            text.get("author_last_name"),
                                            content,
                                            new CdsExportCapturedTextRowDto(
                                                    CdsExportEformRenderingSourceReader.TABLE,
                                                    key,
                                                    fields)))
                            != null) invalid();
            text.clear();
            seen.clear();
            key = null;
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_EFORM_RENDERING_CAPTURE_INVALID");
    }
}
