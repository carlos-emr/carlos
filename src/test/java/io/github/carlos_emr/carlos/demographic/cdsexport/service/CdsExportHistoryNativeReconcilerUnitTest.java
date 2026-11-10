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
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportHistoryNativeReconcilerUnitTest {
    private static final String HASH = "a".repeat(64), RUN = "11111111-1111-4111-8111-111111111111";

    private CdsExportRetainedFieldDto field(CdsCategory category, String path, String value)
            throws Exception {
        var type =
                CdsWireSchema.schemaModel()
                        .element("cds", "OmdCds")
                        .child("cds", "PatientRecord")
                        .child("cds", category.wireName());
        String namespace = "cds";
        for (String part : path.split("/")) {
            String name = part.substring(0, part.indexOf('['));
            var expanded =
                    type.childNames().stream()
                            .filter(n -> n.local().equals(name))
                            .findFirst()
                            .orElseThrow();
            namespace = expanded.namespace();
            type = type.child(namespace, name);
        }
        return new CdsExportRetainedFieldDto(
                CdsImportKeys.hash(path),
                RUN,
                HASH,
                HASH,
                category,
                "/OmdCds[1]/PatientRecord[1]/" + category.wireName() + "[1]/" + path,
                1,
                namespace,
                "{" + type.getTypeNamespace() + "}" + type.getTypeName(),
                null,
                value,
                value,
                null);
    }

    private final CdsExportRetainedSectionWriter.Values streams =
            new CdsExportRetainedSectionWriter.Values() {
                public Reader text(CdsExportRetainedFieldDto field) {
                    return new StringReader(field.logicalInline());
                }

                public InputStream binary(CdsExportRetainedFieldDto field) throws IOException {
                    throw new IOException("UNEXPECTED_BINARY");
                }
            };

    private CdsExportRetainedSectionWriter.Prepared reconcile(
            CdsCategory category, String tag, List<CdsExportRetainedFieldDto> fields)
            throws Exception {
        var original = note(91, "Original imported narrative", tag, List.of());
        var current = note(92, "Current edited narrative", tag, List.of());
        var nativeHistory =
                new CdsExportNativeHistoryMapper()
                        .map(
                                27,
                                category,
                                List.of(original, current),
                                providers(),
                                ZoneId.of("America/Toronto"));
        var prepared =
                new CdsHistoryNativeDto(
                        "Original imported narrative",
                        tag,
                        null,
                        null,
                        "17",
                        null,
                        null,
                        List.of());
        var sourceIds = new HashSet<String>();
        fields.forEach(f -> sourceIds.add(f.id()));
        var source = new CdsExportHistoryRetainedSource.Result(sourceIds, Map.of(), List.of());
        var result =
                new CdsExportHistoryNativeReconciler()
                        .reconcile(
                                27,
                                category,
                                RUN + ":" + HASH,
                                HASH,
                                prepared,
                                original.note().witness().state(),
                                current,
                                fields,
                                source,
                                nativeHistory,
                                streams,
                                () -> {});
        return new CdsExportRetainedSectionWriter()
                .prepareChanges(fields, streams, result.changes(), () -> {});
    }

    @Test
    void shouldKeepOriginalReviewerDetails_whileSerializingAllNativeRevisionsInOneNote()
            throws Exception {
        var fields =
                List.of(
                        field(
                                CdsCategory.CLINICAL_NOTES,
                                "MyClinicalNotesContent[1]",
                                "Original imported narrative"),
                        field(
                                CdsCategory.CLINICAL_NOTES,
                                "NoteReviewer[1]/Name[1]/FirstName[1]",
                                "Reviewer"),
                        field(
                                CdsCategory.CLINICAL_NOTES,
                                "NoteReviewer[1]/Name[1]/LastName[1]",
                                "Fixture"),
                        field(
                                CdsCategory.CLINICAL_NOTES,
                                "NoteReviewer[1]/DateTimeNoteReviewed[1]/YearOnly[1]",
                                "2020"));
        var result = reconcile(CdsCategory.CLINICAL_NOTES, null, fields);
        String xml =
                new String(
                        render(CdsCategory.CLINICAL_NOTES, result.content()),
                        StandardCharsets.UTF_8);
        assertThat(xml)
                .contains(
                        "Original imported narrative",
                        "Current edited narrative",
                        "Reviewer",
                        "2020");
        assertThat(result.retainedFields()).hasSize(3);
        assertThat(result.replacedFields()).containsExactly(fields.getFirst().id());
    }

    @Test
    void
            shouldRetainUnprojectedOriginalCppFields_andAppendNativeMetadataWithoutReplacingResidualInfo()
                    throws Exception {
        var fields =
                List.of(
                        field(
                                CdsCategory.FAMILY_HISTORY,
                                "ProblemDiagnosisProcedureDescription[1]",
                                "Original imported narrative"),
                        field(CdsCategory.FAMILY_HISTORY, "Relationship[1]", "Mother"),
                        field(
                                CdsCategory.FAMILY_HISTORY,
                                "ResidualInfo[1]/DataElement[1]/Name[1]",
                                "Original.Vendor.Value"),
                        field(
                                CdsCategory.FAMILY_HISTORY,
                                "ResidualInfo[1]/DataElement[1]/DataType[1]",
                                "string"),
                        field(
                                CdsCategory.FAMILY_HISTORY,
                                "ResidualInfo[1]/DataElement[1]/Content[1]",
                                "Original clinical supplement"));
        var result = reconcile(CdsCategory.FAMILY_HISTORY, "FamHistory", fields);
        String xml =
                new String(
                        render(CdsCategory.FAMILY_HISTORY, result.content()),
                        StandardCharsets.UTF_8);
        assertThat(xml)
                .contains(
                        "Current edited narrative",
                        "Original imported narrative",
                        "Mother",
                        "Original clinical supplement",
                        "Original.Vendor.Value",
                        "CARLOS.NativeHistory.v1");
        assertThat(result.retainedFields()).hasSize(4);
        assertThat(result.replacedFields()).containsExactly(fields.getFirst().id());
    }

    @Test
    void shouldCreateTheResidualCarrier_whenTheOriginalCppHadNone() throws Exception {
        var fields =
                List.of(
                        field(
                                CdsCategory.FAMILY_HISTORY,
                                "ProblemDiagnosisProcedureDescription[1]",
                                "Original imported narrative"));
        var result = reconcile(CdsCategory.FAMILY_HISTORY, "FamHistory", fields);
        assertThat(
                        new String(
                                render(CdsCategory.FAMILY_HISTORY, result.content()),
                                StandardCharsets.UTF_8))
                .contains("CARLOS.NativeHistory.v1", "Current edited narrative");
    }
}
