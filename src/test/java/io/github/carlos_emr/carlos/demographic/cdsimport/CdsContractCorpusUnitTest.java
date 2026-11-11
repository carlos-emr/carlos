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

package io.github.carlos_emr.carlos.demographic.cdsimport;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto.Precision;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsClinicalNormalizationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Shared real-wire corpus for schema, native installed CLI and authenticated browser verification. */
@Tag("unit")
class CdsContractCorpusUnitTest {
    @TempDir Path temporary;

    @Test void shouldSealEveryCategoryAndPreserveNativeInputs_whenCompleteWireCorpusIsParsed() throws Exception {
        Path source = temporary.resolve("source.xml");
        try (var input = getClass().getResourceAsStream("/demographic/cds-import-all-categories.xml")) {
            assertThat(input).isNotNull(); Files.copy(input, source);
        }
        List<CdsRecordDto> records = new ArrayList<>();
        var parsed = new CdsXmlParser().parse(source, temporary.resolve("spool"),
                CdsInputLimitsDto.defaults(), () -> false, records::add);
        assertThat(parsed.records()).isEqualTo(17);
        assertThat(records).extracting(CdsRecordDto::section).containsExactly(
                "Demographics", "PersonalHistory", "FamilyHistory", "PastHealth", "ProblemList", "RiskFactors",
                "AllergiesAndAdverseReactions", "MedicationsAndTreatments", "Immunizations", "LaboratoryResults",
                "Appointments", "ClinicalNotes", "Reports", "Reports", "CareElements", "AlertsAndSpecialNeeds", "NewCategory");
        var fields = new CdsFieldParser();
        for (CdsRecordDto record : records) fields.verifyRecord(record);
        var normalization = new CdsClinicalNormalizationService(fields);
        var prevention = normalization.immunization(category(records, "Immunizations"));
        assertThat(prevention.date().precision()).isEqualTo(Precision.YEAR);
        assertThat(prevention.date().original()).isEqualTo("2020");
        assertThat(prevention.refused()).isFalse();
        var noteDate = normalization.date(category(records, "ClinicalNotes"), "EventDateTime");
        assertThat(noteDate.precision()).isEqualTo(Precision.FULL_DATE);
        assertThat(noteDate.original()).isEqualTo("2026-02-03");
        var lab = category(records, "LaboratoryResults");
        assertThat(fields.firstText(lab, "PhysiciansNotes", 100)).isEqualTo("PRIVATE SYNTHETIC PHYSICIAN NOTE");
        assertThat(fields.firstText(lab, "ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsPlainText", 50)).isEqualTo("H");
        assertThat(normalization.date(lab, "CollectionDateTime").original()).isEqualTo("2025-03-04T10:20:30.125-05:00");
        var binaryReport = records.stream().filter(r -> r.section().equals("Reports")
                && r.ordinal() == records.stream().filter(v -> v.section().equals("Reports")).mapToLong(CdsRecordDto::ordinal).max().orElseThrow())
                .findFirst().orElseThrow();
        fields.forEachField(binaryReport, field -> {
            if (field.kind().equals("BINARY")) {
                try (var input = fields.openValue(binaryReport, field)) {
                    assertThat(input.readNBytes(8)).containsExactly((byte)137, (byte)80, (byte)78, (byte)71, (byte)13, (byte)10, (byte)26, (byte)10);
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        });
        assertThat(parsed.decodedBinaryBytes()).isEqualTo(68);
    }

    private static CdsRecordDto category(List<CdsRecordDto> records, String name) {
        return records.stream().filter(record -> record.section().equals(name)).findFirst().orElseThrow();
    }
}
