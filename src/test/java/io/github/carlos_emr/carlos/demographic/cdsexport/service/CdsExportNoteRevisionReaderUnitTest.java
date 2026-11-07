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

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit") @Tag("cds-export")
class CdsExportNoteRevisionReaderUnitTest {
    private static final String ERROR = "CDS_EXPORT_NOTE_REVISION_METADATA_INVALID", HASH = "a".repeat(64);
    private Map<String, String> row(long note, String uuid) {
        var row = new LinkedHashMap<String, String>(); row.put("note_id", Long.toString(note)); row.put("demographic_no", "27");
        row.put("uuid", uuid); row.put("observation_date", "2020-02-29 10:11:12"); row.put("update_date", "2026-11-07 12:13:14");
        row.put("archived", null); row.put("signed", "0"); row.put("note", "Native clinical note"); row.put("history", ""); row.put("password", "Never materialize this credential"); return row;
    }
    @Test void shouldStreamRevisionMetadata_withoutBufferingLargeBodiesOrCredentials() throws Exception {
        var row = row(91, "AbCd-Exact-UUID"); row.put("note", "Large 😀 clinical content\r\n".repeat(100000)); row.put("history", "Historical text & literal value".repeat(50000));
        var found = new ArrayList<CdsExportNoteRevisionDto>(); scan(List.of(row), null, found::add, false);
        assertThat(found).containsExactly(new CdsExportNoteRevisionDto(91, 27, "AbCd-Exact-UUID", "2020-02-29 10:11:12", "2026-11-07 12:13:14", null, "0"));
        assertThat(found.getFirst().toString()).isEqualTo("CdsExportNoteRevisionDto[protected]");
    }
    @Test void shouldKeepUuidSpellingAndNullableLegacyMetadata_withoutChoosingTheCurrentRevision() throws Exception {
        var first = row(91, "UUID-case-sensitive-spelling"); var second = row(92, null); second.put("observation_date", null); second.put("update_date", "0000-00-00 00:00:00"); second.put("signed", null); second.put("archived", "1");
        var found = new ArrayList<CdsExportNoteRevisionDto>(); scan(List.of(first, second), null, found::add, false);
        assertThat(found).hasSize(2); assertThat(found.getFirst().uuid()).isEqualTo("UUID-case-sensitive-spelling");
        assertThat(found.getLast().uuid()).isNull(); assertThat(found.getLast().observationDate()).isNull(); assertThat(found.getLast().updateDate()).isEqualTo("0000-00-00 00:00:00");
    }
    @Test void shouldRefuseChangedPatientAndRecordIdentity_beforeCallingTheConsumer() {
        var row = row(91, "UUID"); row.put("demographic_no", "28");
        assertThatThrownBy(() -> scan(List.of(row), null, ignored -> { throw new AssertionError("Foreign metadata emitted"); }, false)).hasMessage(ERROR);
        row.put("demographic_no", "27");
        assertThatThrownBy(() -> scan(List.of(row), CdsExportPatientTableSourceReader.key(92), ignored -> { throw new AssertionError("Substituted identity emitted"); }, false)).hasMessage(ERROR);
    }
    @Test void shouldRequireEveryPhysicalMetadataField_includingExplicitNulls() {
        for (String missing : List.of("note_id", "demographic_no", "uuid", "observation_date", "update_date", "archived", "signed")) {
            var row = row(91, "UUID"); row.remove(missing);
            assertThatThrownBy(() -> scan(List.of(row), CdsExportPatientTableSourceReader.key(91), ignored -> { throw new AssertionError("Incomplete metadata emitted"); }, false)).hasMessage(ERROR);
        }
    }
    @Test void shouldRefuseMalformedNumericIdentities_withoutCoercion() {
        for (String note : List.of("0", "-1", "091", "1x", "9223372036854775808")) {
            var row = row(91, "UUID"); row.put("note_id", note);
            assertThatThrownBy(() -> scan(List.of(row), CdsExportPatientTableSourceReader.key(91), ignored -> { throw new AssertionError("Malformed identity emitted"); }, false)).hasMessage(ERROR);
        }
    }
    @Test void shouldRefuseOversizedMetadata_beforeAllocatingAConsumerBuffer() throws Exception {
        try (var fields = new CdsExportNoteRevisionReader.Fields(27, ignored -> { throw new AssertionError("Oversized metadata emitted"); })) {
            assertThatThrownBy(() -> fields.output(field("uuid", 257))).hasMessage(ERROR);
        }
    }
    @Test void shouldRefuseUnverifiedMetadataDuplicateFields_andLateWrites() throws Exception {
        var fields = new CdsExportNoteRevisionReader.Fields(27, ignored -> {}); var field = field("uuid", 1); var output = fields.output(field); output.write('a');
        assertThatThrownBy(fields::finish).hasMessage(ERROR); fields.verified(field);
        assertThatThrownBy(() -> fields.output(field)).hasMessage(ERROR);
        assertThatThrownBy(() -> output.write('b')).hasMessage(ERROR); fields.close();
        assertThatThrownBy(() -> fields.output(field)).hasMessage(ERROR);
    }
    @Test void shouldPropagateConsumerFailures_withoutReportingSuccess() {
        var failure = new IOException("Owned synthetic metadata consumer failure");
        assertThatThrownBy(() -> scan(List.of(row(91, "UUID")), null, ignored -> { throw failure; }, false)).isSameAs(failure);
    }
    @Test void shouldRequireTheCompleteAuthenticatedBody_beforeFinalMetadataSuccess() {
        var row = row(91, "UUID"); row.put("note", "A body whose authenticated final byte is required");
        assertThatThrownBy(() -> scan(List.of(row), null, ignored -> { throw new AssertionError("Truncated source reported success"); }, true)).isInstanceOf(IOException.class);
    }
    private CdsExportCapturedFieldDto field(String column, long bytes) { return new CdsExportCapturedFieldDto("casemgmt_note", CdsExportPatientTableSourceReader.key(91), column, CdsExportCapturedFieldDto.Kind.UTF8, 0, bytes, HASH); }
    private void scan(List<Map<String, String>> rows, String substitutedKey, CdsExportNoteRevisionReader.Revisions consumer, boolean truncate) throws Exception {
        var values = new ByteArrayOutputStream(); var index = new ByteArrayOutputStream(); var columns = rows.getFirst().keySet();
        new CdsExportNativeCaptureComposer().compose(values, index, Set.of("casemgmt_note"), 1000, 32L << 20, () -> {}, writer -> {
            writer.beginTable("casemgmt_note", columns, rows.size());
            for (var row : rows) writer.row(substitutedKey == null ? CdsExportPatientTableSourceReader.key(Long.parseLong(row.get("note_id"))) : substitutedKey,
                    fields -> { for (var value : row.entrySet()) fields.text(value.getKey(), value.getValue()); });
            writer.endTable();
        });
        byte[] body = values.toByteArray(); if (truncate) body = Arrays.copyOf(body, body.length - 1);
        try (var fields = new CdsExportNoteRevisionReader.Fields(27, consumer)) {
            new CdsExportNativeCaptureReader().scan(new ByteArrayInputStream(body), new ByteArrayInputStream(index.toByteArray()),
                    Map.of("casemgmt_note", new CdsExportNativeCaptureReader.Table(columns, rows.size())), 1000, 32L << 20, () -> {}, fields); fields.finish();
        }
    }
}
