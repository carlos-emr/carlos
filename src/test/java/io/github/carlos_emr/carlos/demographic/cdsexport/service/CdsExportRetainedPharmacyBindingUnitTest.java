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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicRelationsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit") @Tag("cds-export")
class CdsExportRetainedPharmacyBindingUnitTest {
    CdsExportRetainedProfessionalContactBindingUnitTest fixture;
    CdsDemographicRelationsDto.Pharmacy preparation;
    @BeforeEach void setup() {
        fixture = new CdsExportRetainedProfessionalContactBindingUnitTest(); fixture.setup();
        fixture.source.clear(); fixture.originals.clear(); fixture.fixture.preparedRelations(null, null);
        fixture.source.put("DateOfBirth[1]", "1980-01-02");
        source("Name[1]", "Café 😀 Pharmacy"); source("Address[1]/@addressType", "M");
        source("Address[1]/Structured[1]/Line1[1]", "12 Main St"); source("Address[1]/Structured[1]/Line2[1]", "Unit 4");
        source("Address[1]/Structured[1]/Line3[1]", "West Entrance"); source("Address[1]/Structured[1]/City[1]", "Ottawa");
        source("Address[1]/Structured[1]/CountrySubdivisionCode[1]", "CA-ON"); source("Address[1]/Structured[1]/PostalZipCode[1]/PostalCode[1]", "K1A0B1");
        source("PhoneNumber[1]/@phoneNumberType", "W"); source("PhoneNumber[1]/phoneNumber[1]", "6135550110"); source("PhoneNumber[1]/extension[1]", "0012");
        source("FaxNumber[1]/@phoneNumberType", "W"); source("FaxNumber[1]/areaCode[1]", "613"); source("FaxNumber[1]/exchange[1]", "555"); source("FaxNumber[1]/number[1]", "0111");
        source("EmailAddress[1]", "rx@example.test");
        preparation = new CdsDemographicRelationsDto.Pharmacy("Café 😀 Pharmacy", "12 Main St\nUnit 4\nWest Entrance", "Ottawa", "ON", "K1A0B1", "6135550110 ext 0012", "613-555-0111", "rx@example.test");
        fixture.fixture.preparedPharmacy(preparation);
        var pharmacy = CdsExportPharmacyMapperUnitTest.pharmacy(9);
        for (var entry : CdsExportRetainedPharmacyBinding.prepared(preparation).entrySet()) pharmacy = changed(pharmacy, entry.getKey(), entry.getValue());
        fixture.originals.add(pharmacy); fixture.originals.add(CdsExportPharmacyMapperUnitTest.link(1, 9));
    }
    void source(String path, String value) { fixture.source.put("PreferredPharmacy[1]/" + path, value); }
    static CdsExportCapturedTextRowDto changed(CdsExportCapturedTextRowDto row, String column, String value) {
        var fields = new LinkedHashMap<>(row.fields()); fields.put(column, value); return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields);
    }
    static CdsExportRetainedSectionWriter.Values values() {
        return new CdsExportRetainedSectionWriter.Values() {
            public void requireActive() { }
            public Reader text(CdsExportRetainedFieldDto field) { return new StringReader(field.logicalInline() == null ? field.originalInline() : field.logicalInline()); }
            public InputStream binary(CdsExportRetainedFieldDto field) { throw new AssertionError(); }
        };
    }
    CdsExportRetainedPharmacyBinding.Result bind() throws Exception {
        var graph = fixture.graph(); return new CdsExportRetainedPharmacyBinding().bind(27, graph.index().nativeParents().values().iterator().next(),
                graph.index(), graph.checked(), graph.fields(), graph.current(), graph.witness(), values(), () -> { });
    }
    @Test void shouldBindTheExactOriginalPharmacy_throughItsPatientAssociationAndPreparedProjection() throws Exception {
        var result = bind(); assertThat(result.projected()).isTrue(); assertThat(result.association().id()).isEqualTo("1"); assertThat(result.pharmacy().id()).isEqualTo("9");
        assertThat(result.sourceFields()).hasSize(16); assertThat(result.groups().get("Address[1]")).hasSize(7); assertThat(result.preparation()).isEqualTo(preparation);
    }
    @Test void shouldPreserveSourceOnlyPharmacyDetails_withoutInventingAnOriginalNativeProjection() throws Exception {
        fixture.originals.clear(); assertThat(bind().projected()).isFalse(); assertThat(bind().sourceFields()).hasSize(16);
    }
    @Test void shouldRejectAnotherPatient_inTheOriginalAssociation() {
        fixture.originals.set(1, changed(fixture.originals.get(1), "demographic_no", "28")); assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_PHARMACY_PREPARED_PROJECTION_NOT_PROVEN");
    }
    @Test void shouldRejectASameNamePharmacy_withADifferentOriginalForeignKey() {
        fixture.originals.set(1, changed(fixture.originals.get(1), "pharmacyID", "10")); assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_PHARMACY_PREPARED_PROJECTION_NOT_PROVEN");
    }
    @Test void shouldRejectAConflictingSourceValue_beforeRetainingItsUnchangedNativeProjection() {
        source("Address[1]/Structured[1]/Line2[1]", "Another unit"); assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_PHARMACY_SOURCE_PREPARATION_DIFFERS");
    }
    @Test void shouldRejectMultipleOriginalPreferences_withoutSelectingOneByRowOrder() {
        fixture.originals.add(CdsExportPharmacyMapperUnitTest.link(2, 9)); assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_PHARMACY_PREPARED_PROJECTION_NOT_PROVEN");
    }
    @Test void shouldDistinguishSqlNullFromEmpty_inAnOriginalProjectedField() {
        fixture.originals.set(0, changed(fixture.originals.getFirst(), "email", null)); assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_PHARMACY_PREPARED_PROJECTION_NOT_PROVEN");
    }
    @Test void shouldRejectAnotherSourceOccurrence_insteadOfMergingSingularPharmacies() {
        fixture.source.put("PreferredPharmacy[2]/Name[1]", "Another"); assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }
    @Test void shouldProtectTheBoundedPreparedValues_inDebugOutputAndCollections() throws Exception {
        var result = bind(); assertThat(result.toString()).doesNotContain("Café", "Ottawa", "613");
        assertThatThrownBy(() -> result.groups().get("Address[1]").clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
