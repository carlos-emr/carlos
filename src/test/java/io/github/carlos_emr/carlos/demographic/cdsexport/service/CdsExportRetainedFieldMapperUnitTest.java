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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportRetainedFieldMapperUnitTest {
    private final CdsExportRetainedFieldMapper mapper = new CdsExportRetainedFieldMapper();

    static CdsExportCapturedTextRowDto row(
            int ordinal,
            String original,
            String logical,
            CdsExportRetainedFieldDto.Payload payload) {
        var values = new TreeMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.IMPORT_FIELDS
                .columns()
                .forEach(column -> values.put(column, null));
        values.putAll(
                Map.of(
                        "run_id",
                        "11111111-1111-4111-8111-111111111111",
                        "patient_key",
                        "a".repeat(64),
                        "unit_key",
                        "b".repeat(64),
                        "demographic_no",
                        "27",
                        "category",
                        "AllergiesAndAdverseReactions",
                        "source_namespace",
                        "cds",
                        "source_path",
                        "/OmdCds[1]/PatientRecord[1]/AllergiesAndAdverseReactions["
                                + ordinal
                                + "]/Reaction[1]",
                        "occurrence",
                        Integer.toString(ordinal),
                        "value_type",
                        "{cds_dt}text120"));
        values.put(
                "id",
                CdsImportKeys.hash(
                        values.get("unit_key"),
                        values.get("source_namespace"),
                        values.get("source_path"),
                        values.get("occurrence")));
        values.put("original_value", original);
        values.put("normalized_value", logical);
        values.put("payload_offset", payload == null ? "0" : Long.toString(payload.offset()));
        values.put("payload_length", payload == null ? "0" : Long.toString(payload.bytes()));
        if (payload != null) {
            values.put("payload_location", payload.location());
            values.put("payload_digest", payload.sha256());
        }
        return new CdsExportCapturedTextRowDto("cds_import_field", values.get("id"), values);
    }

    @Test
    void shouldKeepOriginalAndAcceptedLogicalValuesDistinct_includingAnExplicitEmptyCorrection()
            throws Exception {
        var source = row(1, "Original café 😀\r\n", "", null);
        var field = mapper.map(27, Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS), source);
        assertThat(field.originalInline()).isEqualTo("Original café 😀\r\n");
        assertThat(field.logicalInline()).isEmpty();
        assertThat(field.originalPayload()).isNull();
        assertThat(field.fieldUnitKey()).isEqualTo("b".repeat(64));
        assertThat(
                        mapper.map(
                                        27,
                                        Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS),
                                        row(1, "", null, null))
                                .logicalInline())
                .isNull();
        assertThat(field.toString()).doesNotContain("Original", "café", "27");
    }

    @Test
    void shouldRetainSpooledOriginalAlongsideAnInlineEffectiveCorrection_forRetainedFieldMapperContract() throws Exception {
        var payload =
                new CdsExportRetainedFieldDto.Payload(
                        Path.of("target", "original.values").toAbsolutePath().toString(),
                        "c".repeat(64),
                        15,
                        200000);
        var field =
                mapper.map(
                        27,
                        Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS),
                        row(1, null, "accepted", payload));
        assertThat(field.originalPayload()).isEqualTo(payload);
        assertThat(field.logicalInline()).isEqualTo("accepted");
        assertThat(payload.toString()).doesNotContain("original.values", "15", "200000");
    }

    @Test
    void shouldRejectWrongIdentityPatientCategoryShapeOrOriginalRepresentation_forRetainedFieldMapperContract() {
        var base = row(1, "original", "logical", null);
        for (Map<String, String> change :
                List.of(
                        Map.of("demographic_no", "28"),
                        Map.of("unit_key", "d".repeat(64)),
                        Map.of("occurrence", "01"),
                        Map.of("source_namespace", "unbound"),
                        Map.of("payload_offset", "1"))) {
            var values = new TreeMap<>(base.fields());
            values.putAll(change);
            assertThatThrownBy(
                            () ->
                                    mapper.map(
                                            27,
                                            Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS),
                                            new CdsExportCapturedTextRowDto(
                                                    base.table(), base.recordKey(), values)))
                    .hasMessage("CDS_EXPORT_RETAINED_FIELD_REQUIRES_REVIEW");
        }
        assertThatThrownBy(() -> mapper.map(27, Set.of(CdsCategory.DEMOGRAPHICS), base))
                .hasMessage("CDS_EXPORT_RETAINED_FIELD_REQUIRES_REVIEW");
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS),
                                        row(1, null, "logical", null)))
                .hasMessage("CDS_EXPORT_RETAINED_FIELD_REQUIRES_REVIEW");
        assertThatThrownBy(
                        () ->
                                new CdsExportRetainedFieldDto.Payload(
                                        "/source", "c".repeat(64), Long.MAX_VALUE, 1))
                .hasMessage("CDS_EXPORT_RETAINED_FIELD_REQUIRES_REVIEW");
    }
}
