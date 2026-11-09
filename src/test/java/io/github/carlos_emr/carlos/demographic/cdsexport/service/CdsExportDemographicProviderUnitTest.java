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
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicProviderUnitTest {
    @TempDir Path root;

    @Test
    void shouldSerializeTheCapturedPrimaryPhysician_throughPinnedSchemaAndReceivingParser()
            throws Exception {
        var mapped =
                new CdsExportDemographicMapper()
                        .map(
                                demographic("0é27"),
                                CdsExportPublicProviderMapperUnitTest.provider("CPSO", "000123"));
        assertThat(mapped.baseRowRepresentable()).isTrue();
        assertThat(mapped.relatedFields()).hasSize(6);
        var category = Set.of(CdsCategory.DEMOGRAPHICS);
        var output = new ByteArrayOutputStream();
        var section =
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(
                                "a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        writer -> new CdsExportDemographicXmlWriter().write(mapped, writer));
        new CdsExportXmlComposer()
                .compose(
                        output,
                        "c".repeat(64),
                        "a".repeat(64),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        category,
                        List.of(section).iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "27",
                        category,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var records = new ArrayList<CdsRecordDto>();
        var file = Files.write(root.resolve("patient.xml"), output.toByteArray());
        new CdsXmlParser()
                .parse(
                        file,
                        root.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        var fields = new CdsFieldParser();
        var record = records.getFirst();
        assertThat(fields.firstText(record, "PrimaryPhysician/Name/FirstName", 50))
                .isEqualTo("Zoë😀");
        assertThat(fields.firstText(record, "PrimaryPhysician/Name/LastName", 50))
                .isEqualTo("D’Arcy");
        assertThat(fields.firstText(record, "PrimaryPhysician/OHIPPhysicianId", 6))
                .isEqualTo("001234");
        assertThat(fields.firstText(record, "PrimaryPhysician/PrimaryPhysicianCPSO", 6))
                .isEqualTo("000123");
    }

    @Test
    void
            shouldRefuseAnUnresolvedOrCollationAliasedPrimaryReference_withoutSubstitutingTheExportOperator()
                    throws Exception {
        var mapper = new CdsExportDemographicMapper();
        assertThat(mapper.map(demographic("0é27")).baseRowRepresentable()).isFalse();
        var result =
                mapper.map(
                        demographic("0É27"),
                        CdsExportPublicProviderMapperUnitTest.provider("CPSO", "000123"));
        assertThat(result.baseRowRepresentable()).isFalse();
        assertThat(result.primaryPhysician()).isNull();
        assertThat(result.fields().get("provider_no").rule())
                .isEqualTo("EXACT_PRIMARY_PHYSICIAN_REFERENCE_REQUIRED");
    }

    static CdsExportDemographicRowDto demographic(String provider) {
        var fields = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(name -> fields.put(name, null));
        fields.putAll(
                Map.of(
                        "first_name",
                        "Synthetic",
                        "last_name",
                        "Fixture",
                        "year_of_birth",
                        "1980",
                        "month_of_birth",
                        "01",
                        "date_of_birth",
                        "01",
                        "patient_status",
                        "AC",
                        "sex",
                        "U"));
        fields.put("provider_no", provider);
        return new CdsExportDemographicRowDto(27, fields);
    }
}
