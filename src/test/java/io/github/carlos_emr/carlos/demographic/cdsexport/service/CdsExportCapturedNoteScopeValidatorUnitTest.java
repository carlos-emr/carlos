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
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit") @Tag("cds-export")
class CdsExportCapturedNoteScopeValidatorUnitTest {
    private static final String HASH = "a".repeat(64), RUN = "11111111-1111-4111-8111-111111111111";
    private final CdsExportNoteAccessDto scope = new CdsExportNoteAccessDto(91, 27, "17", "1");
    private final String key = CdsExportPatientTableSourceReader.key(91);
    private Map<String, String> row() {
        var row = new LinkedHashMap<String, String>(); row.put("note_id", "91"); row.put("demographic_no", "27"); row.put("program_no", "17");
        row.put("reporter_caisi_role", "1"); row.put("locked", null); row.put("note", "Exact native note & literal text"); row.put("password", null); return row;
    }
    @Test void shouldBindEveryPhysicalDependency_withoutChangingTheForwardedNoteBytes() throws Exception {
        var body = new ByteArrayOutputStream(); var data = row();
        scan(data, key, field -> field.column().equals("note") ? body : OutputStream.nullOutputStream());
        assertThat(body.toString(StandardCharsets.UTF_8)).isEqualTo(data.get("note"));
        data.put("locked", "0"); scan(data, key, field -> OutputStream.nullOutputStream());
    }
    @Test void shouldRefuseChangedPatientProgramRoleNoteIdentity_andEveryLockedOrUnknownFlag() {
        for (var changed : Map.of("demographic_no", "28", "program_no", "18", "reporter_caisi_role", "2", "note_id", "92").entrySet()) {
            var data = row(); data.put(changed.getKey(), changed.getValue()); assertThatThrownBy(() -> scan(data, key, field -> OutputStream.nullOutputStream())).hasMessage("CDS_EXPORT_CAPTURE_NOTE_SCOPE_INVALID");
        }
        for (String lock : List.of("1", "Y", "", "false")) {
            var data = row(); data.put("locked", lock); assertThatThrownBy(() -> scan(data, key, field -> OutputStream.nullOutputStream())).hasMessage("CDS_EXPORT_CAPTURE_NOTE_SCOPE_INVALID");
        }
    }
    @Test void shouldRefuseAnUndeclaredNote_evenWhenItsProgramMatches() {
        assertThatThrownBy(() -> scan(row(), CdsExportPatientTableSourceReader.key(92), field -> OutputStream.nullOutputStream())).hasMessage("CDS_EXPORT_CAPTURE_NOTE_SCOPE_INVALID");
    }
    @Test void shouldRequireEveryDependency_includingAnExplicitNullLockField() {
        for (String missing : List.of("note_id", "demographic_no", "program_no", "reporter_caisi_role", "locked")) {
            var data = row(); data.remove(missing); assertThatThrownBy(() -> scan(data, key, field -> OutputStream.nullOutputStream())).hasMessage("CDS_EXPORT_CAPTURE_NOTE_SCOPE_INVALID");
        }
    }
    @Test void shouldRefuseOversizedMetadata_beforeOpeningAConsumerStream() throws Exception {
        try (var validator = new CdsExportCapturedNoteScopeValidator(27, List.of(scope), field -> { throw new AssertionError("Consumer opened before scope rejection"); })) {
            assertThatThrownBy(() -> validator.output(new CdsExportCapturedFieldDto("casemgmt_note", key, "program_no", CdsExportCapturedFieldDto.Kind.UTF8, 0, 129, HASH)))
                    .hasMessage("CDS_EXPORT_CAPTURE_NOTE_SCOPE_INVALID");
        }
    }
    @Test void shouldPropagateAuthenticatedEofAndConsumerFailures_withoutReturningSuccess() {
        var failure = new IOException("Synthetic consumer failure");
        assertThatThrownBy(() -> scan(row(), key, field -> { if (field.column().equals("note")) throw failure; return OutputStream.nullOutputStream(); })).isSameAs(failure);
    }
    @Test void shouldRefuseReuseAfterClose_andRequireCompletedFieldVerification() throws Exception {
        var validator = new CdsExportCapturedNoteScopeValidator(27, List.of(scope), field -> OutputStream.nullOutputStream());
        var field = new CdsExportCapturedFieldDto("casemgmt_note", key, "note_id", CdsExportCapturedFieldDto.Kind.UTF8, 0, 2, HASH);
        validator.output(field).write("91".getBytes(StandardCharsets.UTF_8)); assertThatThrownBy(validator::finish).hasMessage("CDS_EXPORT_CAPTURE_NOTE_SCOPE_INVALID");
        validator.close(); assertThatThrownBy(() -> validator.output(field)).hasMessage("CDS_EXPORT_CAPTURE_NOTE_SCOPE_INVALID");
    }
    @Test void shouldPreserveLegacyManifestShape_withoutInventingNoteDependencies() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper(); var table = new CdsExportCapturedTableDto(Set.of("id"), 1, HASH);
        String encoded = json.writeValueAsString(table); assertThat(encoded).doesNotContain("noteAccess");
        assertThat(json.readValue(encoded, CdsExportCapturedTableDto.class).noteAccess()).isEmpty();
        var noted = new CdsExportCapturedTableDto(row().keySet(), 1, HASH, List.of(), List.of(scope));
        assertThat(json.readValue(json.writeValueAsString(noted), CdsExportCapturedTableDto.class).noteAccess()).containsExactly(scope);
    }
    @Test void shouldRequireOneDeclaredScopePerNativeNote_andItsExactPatient() {
        var empty = new CdsExportCapturedTableDto(row().keySet(), 1, HASH);
        assertThatThrownBy(() -> capture("casemgmt_note", empty)).hasMessage("CDS_EXPORT_CAPTURE_NOTE_ACCESS_INVALID");
        var noted = new CdsExportCapturedTableDto(row().keySet(), 1, HASH, List.of(), List.of(scope));
        assertThat(capture("casemgmt_note", noted).tables().get("casemgmt_note").noteAccess()).containsExactly(scope);
        assertThatThrownBy(() -> capture("other_table", noted)).hasMessage("CDS_EXPORT_CAPTURE_NOTE_ACCESS_INVALID");
        var otherPatient = new CdsExportCapturedTableDto(row().keySet(), 1, HASH, List.of(), List.of(new CdsExportNoteAccessDto(91, 28, "17", "1")));
        assertThatThrownBy(() -> capture("casemgmt_note", otherPatient)).hasMessage("CDS_EXPORT_CAPTURE_NOTE_ACCESS_INVALID");
        assertThatThrownBy(() -> new CdsExportCapturedTableDto(row().keySet(), 2, HASH, List.of(), List.of(scope, scope))).hasMessage("CDS_EXPORT_CAPTURE_NOTE_ACCESS_INVALID");
    }
    private void scan(Map<String, String> row, String recordKey, CdsExportNativeCaptureReader.Fields sink) throws Exception {
        var values = new ByteArrayOutputStream(); var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer().compose(values, index, Set.of("casemgmt_note"), 100, 1L << 20, () -> { }, writer -> {
            writer.beginTable("casemgmt_note", row.keySet(), 1); writer.row(recordKey, fields -> { for (var cell : row.entrySet()) fields.text(cell.getKey(), cell.getValue()); }); writer.endTable();
        });
        try (var validator = new CdsExportCapturedNoteScopeValidator(27, List.of(scope), sink)) {
            new CdsExportNativeCaptureReader().scan(new ByteArrayInputStream(values.toByteArray()), new ByteArrayInputStream(index.toByteArray()),
                    Map.of("casemgmt_note", new CdsExportNativeCaptureReader.Table(row.keySet(), 1)), 100, 1L << 20, () -> { }, validator);
            validator.finish();
        }
    }
    private CdsExportNativeCaptureDto capture(String name, CdsExportCapturedTableDto table) {
        return new CdsExportNativeCaptureDto(2, RUN, 1, 27, HASH, Instant.EPOCH, Instant.EPOCH, Map.of(name, table), table.columns().size(), 0,
                new CdsExportStagedFileDto("b".repeat(64), "SOURCE_VALUES", HASH, 0), new CdsExportStagedFileDto("c".repeat(64), "SOURCE_INDEX", HASH, 0));
    }
}
