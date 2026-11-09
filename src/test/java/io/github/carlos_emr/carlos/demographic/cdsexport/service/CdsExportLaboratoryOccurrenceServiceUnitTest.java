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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportLaboratoryOccurrenceServiceUnitTest {
    private static final String ID = "a".repeat(64);
    private final Map<String, CdsExportCapturedTextRowDto> current = new HashMap<>();

    @BeforeEach
    void setup() {
        put(
                "cds_laboratory_result",
                ID,
                fields(
                        "id",
                        ID,
                        "demographicNo",
                        "27",
                        "nativeLabNo",
                        "91",
                        "sourceBlocked",
                        null));
        put("hl7TextMessage", "91", fields("lab_id", "91"));
        put("hl7TextInfo", "92", fields("id", "92", "lab_no", "91"));
        put(
                "patientLabRouting",
                "93",
                fields("id", "93", "lab_no", "91", "demographic_no", "27", "lab_type", "HL7"));
    }

    private static Map<String, String> fields(String... entries) {
        var values = new HashMap<String, String>();
        for (int i = 0; i < entries.length; i += 2) values.put(entries[i], entries[i + 1]);
        return values;
    }

    private void put(String table, String id, Map<String, String> fields) {
        current.put(
                table + ":" + id,
                new CdsExportCapturedTextRowDto(
                        table,
                        table.equals("cds_laboratory_result")
                                ? id
                                : CdsExportPatientTableSourceReader.key(Long.parseLong(id)),
                        fields));
    }

    private void change(String key, String column, String value) {
        var row = current.get(key);
        var fields = new HashMap<>(row.fields());
        fields.put(column, value);
        current.put(key, new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields));
    }

    private void ownership() throws Exception {
        CdsExportLaboratoryOccurrenceService.requireOwnership(27, current);
    }

    @Test
    void shouldBindTheWholeStructuredResultMessageMetadataAndRouting_asOneOccurrence() {
        assertThatCode(this::ownership).doesNotThrowAnyException();
    }

    @Test
    void shouldKeepABlockedResultWithoutAnUnrestrictedMirror() {
        current.clear();
        put(
                "cds_laboratory_result",
                ID,
                fields("id", ID, "demographicNo", "27", "nativeLabNo", null, "sourceBlocked", "Y"));
        assertThatCode(this::ownership).doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseACrossPatientStructuredResult() {
        change("cds_laboratory_result:" + ID, "demographicNo", "28");
        assertThatThrownBy(this::ownership).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void shouldRefuseCrossPatientRouting() {
        change("patientLabRouting:93", "demographic_no", "28");
        assertThatThrownBy(this::ownership).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseANonHl7RoutingType_insteadOfReinterpretingItsNumericLabId() {
        change("patientLabRouting:93", "lab_type", "DOC");
        assertThatThrownBy(this::ownership).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseDuplicatePatientRoutes() {
        put(
                "patientLabRouting",
                "94",
                fields("id", "94", "lab_no", "91", "demographic_no", "27", "lab_type", "HL7"));
        assertThatThrownBy(this::ownership).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseDuplicateLabMetadata() {
        put("hl7TextInfo", "94", fields("id", "94", "lab_no", "91"));
        assertThatThrownBy(this::ownership).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseADeletedMessage_insteadOfShrinkingTheOccurrence() {
        current.remove("hl7TextMessage:91");
        assertThatThrownBy(this::ownership).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseMissingRouting() {
        current.remove("patientLabRouting:93");
        assertThatThrownBy(this::ownership).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseMissingLabMetadata() {
        current.remove("hl7TextInfo:92");
        assertThatThrownBy(this::ownership).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseANativeOnlyHl7_withoutReplayingUnrelatedRetainedXml() {
        current.remove("cds_laboratory_result:" + ID);
        assertThatThrownBy(this::ownership).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseABlockedResultLinkedToAnUnrestrictedMirror() {
        change("cds_laboratory_result:" + ID, "sourceBlocked", "Y");
        assertThatThrownBy(this::ownership).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseTwoStructuredResultsSharingOneMessage() {
        String other = "b".repeat(64);
        put(
                "cds_laboratory_result",
                other,
                fields(
                        "id",
                        other,
                        "demographicNo",
                        "27",
                        "nativeLabNo",
                        "91",
                        "sourceBlocked",
                        null));
        assertThatThrownBy(this::ownership).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseAnUnblockedResultWithNoNativeReference() {
        change("cds_laboratory_result:" + ID, "nativeLabNo", null);
        assertThatThrownBy(this::ownership).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldKeepAnOriginallyUnshiftedDate_withoutNormalizingItsSpelling() throws Exception {
        String lexical = "2020-02-29T24:00:00Z";
        var date = CdsTemporal.parse(null, null, null, lexical, "Collection");
        assertThat(
                        CdsExportLaboratoryOccurrenceService.nativeDate(
                                date, lexical, date.precision().name()))
                .isTrue();
        assertThat(
                        CdsExportLaboratoryOccurrenceService.nativeDate(
                                date, "2020-03-01T00:00:00Z", date.precision().name()))
                .isFalse();
    }

    @Test
    void shouldRecognizeTheImportersNormalizedNativeAnchor_afterAnOriginalShift() throws Exception {
        var date =
                CdsTemporal.shift(
                        CdsTemporal.parse(null, null, null, "2020-02-29T24:00:00Z", "Collection"),
                        2,
                        "Collection");
        assertThat(
                        CdsExportLaboratoryOccurrenceService.nativeDate(
                                date, "2020-03-03T00:00:00Z", date.precision().name()))
                .isTrue();
        assertThat(
                        CdsExportLaboratoryOccurrenceService.nativeDate(
                                date, "2020-03-02T00:00:00Z", date.precision().name()))
                .isFalse();
    }

    @Test
    void shouldRefuseAChangedNativeOffset_insteadOfBorrowingTheOriginalPrecision()
            throws Exception {
        var date =
                CdsTemporal.shift(
                        CdsTemporal.parse(
                                null, null, null, "2020-02-28T12:23:34.120-05:00", "Collection"),
                        2,
                        "Collection");
        assertThat(
                        CdsExportLaboratoryOccurrenceService.nativeDate(
                                date, "2020-03-01T12:23:34.120-05:00", date.precision().name()))
                .isTrue();
        assertThat(
                        CdsExportLaboratoryOccurrenceService.nativeDate(
                                date, "2020-03-01T17:23:34.120Z", date.precision().name()))
                .isFalse();
    }

    @Test
    void shouldRefuseAnUnknownNativePrecision_withoutAnInventedDate() throws Exception {
        assertThat(
                        CdsExportLaboratoryOccurrenceService.nativeDate(
                                CdsTemporalDto.unknown(), null, "UNKNOWN"))
                .isTrue();
        assertThat(
                        CdsExportLaboratoryOccurrenceService.nativeDate(
                                CdsTemporalDto.unknown(), "2020", "UNKNOWN"))
                .isFalse();
        assertThat(
                        CdsExportLaboratoryOccurrenceService.nativeDate(
                                CdsTemporalDto.unknown(), null, "YEAR"))
                .isFalse();
    }
}
