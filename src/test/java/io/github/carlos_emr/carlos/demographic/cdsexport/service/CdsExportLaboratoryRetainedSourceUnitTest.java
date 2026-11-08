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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit") @Tag("cds-export")
class CdsExportLaboratoryRetainedSourceUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111", HASH = "a".repeat(64);
    private final List<CdsExportRetainedFieldDto> fields = new ArrayList<>();
    private final Map<String, String> scalars = new HashMap<>();
    private CdsExportRetainedSectionWriter.Values values;
    private CdsTemporalDto collection, requisition;
    private int shift;
    @BeforeEach void setup() throws Exception {
        shift = 0; values = mock(CdsExportRetainedSectionWriter.Values.class);
        when(values.text(any())).thenAnswer(call -> new StringReader(call.getArgument(0, CdsExportRetainedFieldDto.class).logicalInline()));
        add("LaboratoryName[1]", "Exact Laboratory"); add("CollectionDateTime[1]/FullDateTime[1]", "2020-02-28T12:23:34.120-05:00");
        add("ResultNormalAbnormalFlag[1]/resultNormalAbnormalFlagAsEnum[1]", "N");
        scalars.put("LaboratoryName", "Exact Laboratory"); scalars.put("ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsEnum", "N");
        collection = temporal("2020-02-28T12:23:34.120-05:00", true); requisition = CdsTemporalDto.unknown();
    }
    private CdsExportLaboratoryRetainedSource.Result read() throws Exception {
        return new CdsExportLaboratoryRetainedSource().readVerified(fields,
                new CdsExportRetainedImportOptionsReader.Options(RUN, HASH, HASH, HASH, "omd-5.2", shift != 0, shift),
                new CdsLaboratoryNativeDto(scalars, collection, requisition), values, () -> {});
    }
    @Test void shouldPreserveEveryOriginalField_andTheExactOffsetAndFraction() throws Exception {
        add("PhysiciansNotes[1]", "Provider-only clinical detail"); scalars.put("PhysiciansNotes", "Provider-only clinical detail");
        var result = read(); assertThat(result.sourceFields()).hasSize(4); assertThat(result.changes()).isEmpty();
        assertThat(result.dates().get("CollectionDateTime[1]").lexical()).isEqualTo("2020-02-28T12:23:34.120-05:00");
    }
    @Test void shouldShiftCollectionRequisitionAndEveryReviewerDate_usingOriginalOptions() throws Exception {
        shift = 2; collection = temporal("2020-02-28T12:23:34.120-05:00", true); requisition = temporal("2020-02-29", false);
        add("LabRequisitionDateTime[1]/FullDate[1]", "2020-02-29"); add("ResultReviewer[1]/Name[1]/FirstName[1]", "Synthetic"); add("ResultReviewer[1]/Name[1]/LastName[1]", "Reviewer");
        add("ResultReviewer[1]/DateTimeResultReviewed[1]/FullDateTime[1]", "2020-02-29T13:00:00.1200Z");
        var result = read(); assertThat(result.changes()).hasSize(3);
        assertThat(result.dates().get("CollectionDateTime[1]").lexical()).isEqualTo("2020-03-01T12:23:34.120-05:00");
        assertThat(result.dates().get("LabRequisitionDateTime[1]").lexical()).isEqualTo("2020-03-02");
        assertThat(result.dates().get("ResultReviewer[1]/DateTimeResultReviewed[1]").lexical()).isEqualTo("2020-03-02T13:00:00.1200Z");
        var xml = mock(CdsExportXmlComposer.ContentWriter.class); result.changes().getLast().content().write(xml); verify(xml).text("2020-03-02T13:00:00.1200Z");
    }
    @Test void shouldKeepLegalEndOfDaySpelling_whenShiftingTheSourceClock() throws Exception {
        fields.remove(1); add("CollectionDateTime[1]/FullDateTime[1]", "2020-02-29T24:00:00Z"); shift = 1; collection = temporal("2020-02-29T24:00:00Z", true);
        assertThat(read().dates().get("CollectionDateTime[1]").lexical()).isEqualTo("2020-03-01T24:00:00Z");
    }
    @Test void shouldKeepYearAndMonthPrecision_withoutInventingADay() throws Exception {
        fields.remove(1); add("CollectionDateTime[1]/YearOnly[1]", "2020"); collection = CdsTemporal.parse("2020", null, null, null, "CollectionDateTime");
        assertThat(read().dates().get("CollectionDateTime[1]").lexical()).isEqualTo("2020");
        fields.removeLast(); add("CollectionDateTime[1]/YearMonth[1]", "2020-02"); collection = CdsTemporal.parse(null, "2020-02", null, null, "CollectionDateTime");
        assertThat(read().dates().get("CollectionDateTime[1]").lexical()).isEqualTo("2020-02");
    }
    @Test void shouldRetainTheBlockedIndicator_inItsStandardField() throws Exception {
        add("BlockedTestResult[1]", "Y"); scalars.put("BlockedTestResult", "Y");
        assertThat(read().sourceFields()).hasSize(4);
    }
    @Test void shouldPreserveXmlTokenWhitespaceSemantics_withoutStrippingUnicodeSpaces() throws Exception {
        add("TestNameReportedByLab[1]", "  A\tB\nC  "); scalars.put("TestNameReportedByLab", "A B C"); assertThat(read().sourceFields()).hasSize(4);
        fields.removeLast(); add("TestNameReportedByLab[1]", "\u2003A\u2003"); scalars.put("TestNameReportedByLab", "\u2003A\u2003"); assertThat(read().sourceFields()).hasSize(4);
    }
    @Test void shouldRefuseAChangedKnownScalar_insteadOfTrustingTheOriginalXml() {
        scalars.put("LaboratoryName", "A different laboratory"); assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_LABORATORY_SOURCE_PREPARATION_MISMATCH");
    }
    @Test void shouldRefuseAPreparedScalarWithNoRetainedField() {
        scalars.put("PhysiciansNotes", "Unwitnessed"); assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_LABORATORY_SOURCE_PREPARATION_MISMATCH");
    }
    @Test void shouldRefuseAChangedPreparedDate_orUnrecordedShift() {
        collection = temporal("2020-02-29T12:23:34.120-05:00", true); assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_LABORATORY_SOURCE_PREPARATION_MISMATCH");
    }
    @Test void shouldRefuseAnotherImportRun_orPatientWithinOneOccurrence() {
        var f = fields.getFirst(); fields.set(0, new CdsExportRetainedFieldDto(f.id(), "22222222-2222-4222-8222-222222222222", f.sourcePatientKey(), f.fieldUnitKey(), f.category(), f.sourcePath(), f.occurrence(), f.namespace(), f.valueType(), f.precision(), f.originalInline(), f.logicalInline(), f.originalPayload()));
        assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }
    @Test void shouldRefuseARepeatedDateChoice_insteadOfSelectingOne() {
        add("CollectionDateTime[1]/FullDate[1]", "2020-02-28"); assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }
    @Test void shouldRefuseDuplicateFieldIdentities_beforeReturningACoverageClaim() {
        fields.add(fields.getFirst()); assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }
    @Test void shouldRefuseExpiredValues_beforeReadingAnyPayload() throws Exception {
        doThrow(new IOException("EXPIRED")).when(values).requireActive(); assertThatThrownBy(this::read).hasMessage("EXPIRED"); verify(values, never()).text(any());
    }
    @Test void shouldPropagateCancellation_beforeSourceReadback() {
        assertThatThrownBy(() -> new CdsExportLaboratoryRetainedSource().readVerified(fields,
                new CdsExportRetainedImportOptionsReader.Options(RUN, HASH, HASH, HASH, "omd-5.2", false, 0), new CdsLaboratoryNativeDto(scalars, collection, requisition), values,
                () -> { throw new java.util.concurrent.CancellationException("CANCELLED"); })).hasMessage("CANCELLED");
    }
    private CdsTemporalDto temporal(String lexical, boolean clock) { return CdsTemporal.shift(CdsTemporal.parse(null, null, clock ? null : lexical, clock ? lexical : null, "Date"), shift, "Date"); }
    private void add(String path, String value) {
        var type = CdsWireSchema.schemaModel().element("cds", "OmdCds").child("cds", "PatientRecord").child("cds", "LaboratoryResults"); String namespace = "cds";
        for (String segment : path.split("/")) {
            String name = segment.substring(0, segment.indexOf('[')); var child = type.childNames().stream().filter(candidate -> candidate.local().equals(name)).findFirst().orElseThrow(); namespace = child.namespace(); type = type.child(namespace, name);
        }
        fields.add(new CdsExportRetainedFieldDto(CdsImportKeys.hash(path, Integer.toString(fields.size())), RUN, HASH, HASH, CdsCategory.LABORATORY_RESULTS,
                "/OmdCds[1]/PatientRecord[1]/LaboratoryResults[1]/" + path, 1, namespace, "{" + type.getTypeNamespace() + "}" + type.getTypeName(), null, value, value, null));
    }
}
