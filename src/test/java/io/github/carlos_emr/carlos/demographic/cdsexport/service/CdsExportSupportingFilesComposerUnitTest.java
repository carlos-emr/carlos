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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportHeldPatientDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMetadataDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientSummaryDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPhysicianSummaryDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportSupportingFilesComposerUnitTest {
    private final CdsExportSupportingFilesComposer composer = new CdsExportSupportingFilesComposer();

    @Test void shouldWriteRequiredReadMeFieldsAndActualCategoryCounts_forAFullExport() throws Exception {
        var readMe = new ByteArrayOutputStream();
        var events = new ByteArrayOutputStream();
        var categories = EnumSet.allOf(CdsCategory.class);
        Map<CdsCategory, Long> counts = new EnumMap<>(CdsCategory.class);
        categories.forEach(category -> counts.put(category, 0L));
        counts.put(CdsCategory.DEMOGRAPHICS, 1L);
        counts.put(CdsCategory.LABORATORY_RESULTS, 7L);
        counts.put(CdsCategory.REPORTS, 3L);
        var summary = new CdsExportPatientSummaryDto(patient("C27", "First"), counts, 2L, 2L, "a".repeat(64), 1234);
        var totals = composer.compose(readMe, events, metadata(categories),
                List.of(physician(1)).iterator(), List.of(summary).iterator(), () -> {});
        assertThat(totals.patientFiles()).isEqualTo(1);
        assertThat(totals.physicians()).isEqualTo(1);
        String text = readMe.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("Physician group: \"Fixture group\"",
                "Group contact information: \"Fixture contact\"",
                "Offering version: \"2026.08.0-beta7-SNAPSHOT\"",
                "2026-11-04T16:00:00-05:00", "Physician: \"Alice Test\"",
                "Physician patient files: \"1\"",
                "ExportRoot/Alice_Test_123456/First_Test_C27_04031971.xml",
                "Audit information access after migration: \"Fixture retained audit access arrangement\"",
                "Prevention screenings (exported only as PastHealth): \"INCLUDED\"",
                // D3: the receiver is told which practice-administrative demographic fields are not exported.
                "Practice-administrative demographic fields \"date joined\" and \"effective date\" are not part of"
                        + " CDS 5.2 Demographics and are not exported.");
        String[] lines = events.toString(StandardCharsets.UTF_8).split("\n");
        assertThat(lines).hasSize(2);
        String[] columns = lines[0].split(","), values = lines[1].split(",");
        assertThat(columns).hasSize(19);
        Map<String, String> byColumn = new java.util.HashMap<>();
        for (int i = 0; i < columns.length; i++) byColumn.put(columns[i], values[i]);
        assertThat(byColumn).containsEntry("PatientID", "\"C27\"")
                .containsEntry("LaboratoryResults", "\"7\"")
                .containsEntry("Reports", "\"3\"")
                .containsEntry("ReportsText", "\"2\"")
                .containsEntry("ReportsBinary", "\"2\"")
                .containsEntry("NewCategory", "\"0\"");
    }

    @Test void shouldDistinguishNotSelectedFromKnownZero_forSelectedCategoryExports() throws Exception {
        var events = new ByteArrayOutputStream();
        Set<CdsCategory> categories = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS);
        var patient = new CdsExportPatientSummaryDto(patient("C27", "First"),
                Map.of(CdsCategory.DEMOGRAPHICS, 1L, CdsCategory.LABORATORY_RESULTS, 0L),
                null, null, "a".repeat(64), 123);
        composer.compose(new ByteArrayOutputStream(), events, metadata(categories),
                List.of(physician(1)).iterator(), List.of(patient).iterator(), () -> {});
        String text = events.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("\"NOT_SELECTED\"").contains("\"0\"");
        assertThat(text.lines().toList().get(1)).startsWith("\"C27\",\"1\",\"NOT_SELECTED\"");
    }

    // A package without PastHealth must not be read as carrying the screening history: CARLOS
    // keeps screenings with the vaccines, but CDS carries them only as PastHealth procedures.
    @Test void shouldStateScreeningsAreNotIncluded_whenPastHealthWasNotSelected() throws Exception {
        var readMe = new ByteArrayOutputStream();
        Set<CdsCategory> categories = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.IMMUNIZATIONS);
        var patient = new CdsExportPatientSummaryDto(patient("C27", "First"),
                Map.of(CdsCategory.DEMOGRAPHICS, 1L, CdsCategory.IMMUNIZATIONS, 0L),
                null, null, "a".repeat(64), 123);
        composer.compose(readMe, new ByteArrayOutputStream(), metadata(categories),
                List.of(physician(1)).iterator(), List.of(patient).iterator(), () -> {});
        assertThat(readMe.toString(StandardCharsets.UTF_8))
                .contains("Prevention screenings (exported only as PastHealth): \"NOT_SELECTED\"");
    }

    @Test void shouldRejectMissingSelectedCategoryCounts_insteadOfTreatingThemAsZero() {
        Set<CdsCategory> categories = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS);
        var patient = new CdsExportPatientSummaryDto(patient("C27", "First"),
                Map.of(CdsCategory.DEMOGRAPHICS, 1L), null, null, "a".repeat(64), 123);
        assertThatThrownBy(() -> composer.compose(new ByteArrayOutputStream(), new ByteArrayOutputStream(),
                metadata(categories), List.of(physician(1)).iterator(), List.of(patient).iterator(), () -> {}))
                .hasMessage("CDS_EXPORT_CATEGORY_COUNTS_INCOMPLETE");
    }

    @Test void shouldReconcilePhysicianTotals_withActualPatientFileInventory() {
        var patient = demographicSummary("C27", "First");
        assertThatThrownBy(() -> composer.compose(new ByteArrayOutputStream(), new ByteArrayOutputStream(),
                metadata(Set.of(CdsCategory.DEMOGRAPHICS)), List.of(physician(2)).iterator(),
                List.of(patient).iterator(), () -> {})).hasMessage("CDS_EXPORT_PHYSICIAN_TOTAL_MISMATCH");
    }

    @Test void shouldRejectDuplicateOrUnorderedPatientPaths_withoutReturningSuccessfulTotals() {
        var first = demographicSummary("C27", "First");
        var later = demographicSummary("C28", "Later");
        assertThatThrownBy(() -> composer.compose(new ByteArrayOutputStream(), new ByteArrayOutputStream(),
                metadata(Set.of(CdsCategory.DEMOGRAPHICS)), List.of(physician(2)).iterator(),
                List.of(first, first).iterator(), () -> {})).hasMessage("CDS_EXPORT_PATIENT_FILE_ORDER_INVALID");
        assertThatThrownBy(() -> composer.compose(new ByteArrayOutputStream(), new ByteArrayOutputStream(),
                metadata(Set.of(CdsCategory.DEMOGRAPHICS)), List.of(physician(2)).iterator(),
                List.of(later, first).iterator(), () -> {})).hasMessage("CDS_EXPORT_PATIENT_FILE_ORDER_INVALID");
    }

    @Test void shouldRequireExplicitAuditAccessInformation_beforeComposingSupportingFiles() {
        var m = metadata(Set.of(CdsCategory.DEMOGRAPHICS));
        var missing = new CdsExportPackageMetadataDto(m.exportRoot(), m.physicianGroup(),
                m.groupContactInformation(), m.applicationVersion(), m.exportTime(), m.clinicalZone(),
                m.profile(), m.categories(), null);
        assertThatThrownBy(() -> composer.compose(new ByteArrayOutputStream(), new ByteArrayOutputStream(),
                missing, List.of(physician(1)).iterator(), List.of(demographicSummary("C27", "First")).iterator(),
                () -> {})).hasMessage("CDS_EXPORT_PACKAGE_METADATA_REQUIRED");
    }

    @Test void shouldPropagateSupportingFileDeliveryFailure_withoutReturningSuccess() {
        OutputStream failing = new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("fixture output failure"); }
        };
        assertThatThrownBy(() -> composer.compose(new ByteArrayOutputStream(), failing,
                metadata(Set.of(CdsCategory.DEMOGRAPHICS)), List.of(physician(1)).iterator(),
                List.of(demographicSummary("C27", "First")).iterator(), () -> {}))
                .hasMessage("fixture output failure");
    }

    @Test void shouldPropagateCancellation_beforeAnyPatientIsCounted() {
        assertThatThrownBy(() -> composer.compose(new ByteArrayOutputStream(), new ByteArrayOutputStream(),
                metadata(Set.of(CdsCategory.DEMOGRAPHICS)), List.of(physician(1)).iterator(),
                List.of(demographicSummary("C27", "First")).iterator(),
                () -> {throw new IllegalStateException("fixture cancelled");})).hasMessage("fixture cancelled");
    }

    @Test void shouldListHeldPatientsAndUnreviewedLocalTables_inTheReadMe() throws Exception {
        var readMe = new ByteArrayOutputStream();
        var base = metadata(Set.of(CdsCategory.DEMOGRAPHICS));
        var partial = new CdsExportPackageMetadataDto(base.exportRoot(), base.physicianGroup(),
                base.groupContactInformation(), base.applicationVersion(), base.exportTime(),
                base.clinicalZone(), base.profile(), base.categories(), base.auditAccessDescription(),
                List.of(new CdsExportHeldPatientDto(41, "CDS_EXPORT_UNMAPPED_DATA")),
                List.of(new io.github.carlos_emr.carlos.commn.dto.CdsExportLocalSourceDto("clinic_notes_local", 7)));
        composer.compose(readMe, new ByteArrayOutputStream(), partial, List.of(physician(1)).iterator(),
                List.of(demographicSummary("C27", "First")).iterator(), () -> {});
        assertThat(readMe.toString(StandardCharsets.UTF_8)).contains(
                "Held patients not in this package: \"1\"\nHeld patient: \"41\"\n"
                        + "Held patient reason: \"CDS_EXPORT_UNMAPPED_DATA\"\n",
                "Unreviewed local tables not exported: \"1\"\nUnreviewed local table: \"clinic_notes_local\"\n"
                        + "Unreviewed local table estimated rows: \"7\"\n");
    }

    @Test void shouldStateNoHeldPatientsOrLocalTables_forACompletePackage() throws Exception {
        var readMe = new ByteArrayOutputStream();
        composer.compose(readMe, new ByteArrayOutputStream(), metadata(Set.of(CdsCategory.DEMOGRAPHICS)),
                List.of(physician(1)).iterator(), List.of(demographicSummary("C27", "First")).iterator(), () -> {});
        assertThat(readMe.toString(StandardCharsets.UTF_8)).contains(
                "Held patients not in this package: \"0\"\n", "Unreviewed local tables not exported: \"0\"\n");
    }

    private CdsExportPackageMetadataDto metadata(Set<CdsCategory> categories) {
        return new CdsExportPackageMetadataDto("ExportRoot", "Fixture group", "Fixture contact",
                "2026.08.0-beta7-SNAPSHOT", Instant.parse("2026-11-04T21:00:00Z"),
                ZoneId.of("America/Toronto"), CdsLogicalProfile.ONTARIO_MD_5_2, categories,
                "Fixture retained audit access arrangement");
    }

    private CdsExportPhysicianSummaryDto physician(long count) {
        return new CdsExportPhysicianSummaryDto("Alice", "Test", "123456", count);
    }

    private CdsExportPatientFileDto patient(String id, String first) {
        return new CdsExportPatientFileDto("Alice", "Test", "123456", first, "Test", id,
                LocalDate.of(1971, 3, 4));
    }

    private CdsExportPatientSummaryDto demographicSummary(String id, String first) {
        return new CdsExportPatientSummaryDto(patient(id, first),
                Map.of(CdsCategory.DEMOGRAPHICS, 1L), null, null, "a".repeat(64), 123);
    }
}
