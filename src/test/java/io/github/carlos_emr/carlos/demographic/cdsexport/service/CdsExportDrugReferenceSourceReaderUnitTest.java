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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDrugReferenceInputDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDrugReferenceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportDrugReferenceSourceReaderUnitTest {
    static CdsExportDrugReferenceDto reference() {
        return new CdsExportDrugReferenceDto(
                1,
                CdsExportDrugReferenceDto.AUTHORITY,
                "00123456",
                "Native reference tablet",
                List.of(new CdsExportDrugReferenceDto.Component("Native ingredient", "5", "mg")));
    }

    static CdsExportDrugReferenceInputDto input(long id, long catalogue) {
        return new CdsExportDrugReferenceInputDto(id, BigInteger.valueOf(catalogue), "00123456");
    }

    @Test
    void shouldFreezeDeduplicatedPublicReferences_andBindThemToTheActualDrugSnapshot()
            throws Exception {
        var drugs = mock(DrugDao.class);
        var projection = mock(CdsExportDrugReferenceProjectionService.class);
        var lookup = mock(CdsExportDrugReferenceLookupService.class);
        var control = mock(CdsExportRunControl.class);
        var inputs = List.of(input(50, 0), input(51, 17), input(52, 17));
        when(projection.read(27)).thenReturn(inputs);
        when(drugs.readCdsExportDrugReferenceInputs(27, 8192)).thenReturn(inputs);
        when(lookup.read("00123456")).thenReturn(reference());
        var singleton = new CdsExportDrugReferenceSourceReader(drugs, projection, lookup);
        var scoped = singleton.forCapture(27, control);
        assertThat(scoped.count(27)).isEqualTo(2);
        verify(lookup, times(1)).read("00123456");
        assertThat(new CdsExportSourceSchemaValidator().verify(scoped.schema(), scoped.columns()))
                .matches("[0-9a-f]{64}");
        var writer = mock(CdsExportNativeCaptureComposer.Writer.class);
        var row = mock(CdsExportNativeCaptureComposer.RowWriter.class);
        var emitted = new ArrayList<Map<String, String>>();
        var current = new LinkedHashMap<String, String>();
        doAnswer(
                        call -> {
                            current.put(call.getArgument(0), call.getArgument(1));
                            return null;
                        })
                .when(row)
                .text(anyString(), any(String.class));
        doAnswer(
                        call -> {
                            ((CdsExportNativeCaptureComposer.RowContent) call.getArgument(1))
                                    .capture(row);
                            emitted.add(Map.copyOf(current));
                            current.clear();
                            return null;
                        })
                .when(writer)
                .row(anyString(), any(CdsExportNativeCaptureComposer.RowContent.class));
        scoped.capture(27, writer);
        assertThat(emitted).hasSize(2);
        assertThat(emitted.getFirst()).containsEntry("drugid", "51");
        assertThat(emitted.getFirst().get("public_reference"))
                .contains("HEALTH_CANADA_DIN_VIA_DRUGREF", "Native ingredient");
        when(drugs.readCdsExportDrugReferenceInputs(27, 8192)).thenReturn(List.of(input(51, 18)));
        assertThatThrownBy(() -> scoped.count(27)).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
        assertThatThrownBy(() -> singleton.count(27)).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
    }

    @Test
    void shouldLeaveCustomDrugIdentifiersUnqualified_withoutCallingTheReferenceService()
            throws Exception {
        var drugs = mock(DrugDao.class);
        var projection = new CdsExportDrugReferenceProjectionService(drugs);
        var lookup = mock(CdsExportDrugReferenceLookupService.class);
        when(drugs.readCdsExportDrugReferenceInputs(27, 8192)).thenReturn(List.of(input(51, 0)));
        var scoped =
                new CdsExportDrugReferenceSourceReader(drugs, projection, lookup)
                        .forCapture(27, mock(CdsExportRunControl.class));
        assertThat(scoped.count(27)).isZero();
        verifyNoInteractions(lookup);
    }

    @Test
    void shouldLeaveACatalogueDrugWithoutAKnownDinUnqualified_insteadOfHoldingThePatient()
            throws Exception {
        // A21: a catalogue selection whose stored identifier is not a DIN, or whose DIN DrugRef no
        // longer holds, gets no public reference; the mapper exports it without a DIN element.
        var drugs = mock(DrugDao.class);
        var projection = mock(CdsExportDrugReferenceProjectionService.class);
        var lookup = mock(CdsExportDrugReferenceLookupService.class);
        var inputs =
                List.of(
                        new CdsExportDrugReferenceInputDto(51, BigInteger.valueOf(17), "not a DIN"),
                        new CdsExportDrugReferenceInputDto(52, BigInteger.valueOf(17), null),
                        new CdsExportDrugReferenceInputDto(53, BigInteger.valueOf(17), "00999999"),
                        new CdsExportDrugReferenceInputDto(54, BigInteger.valueOf(18), "00999999"),
                        input(55, 17));
        when(projection.read(27)).thenReturn(inputs);
        when(drugs.readCdsExportDrugReferenceInputs(27, 8192)).thenReturn(inputs);
        when(lookup.read("00999999")).thenReturn(null);
        when(lookup.read("00123456")).thenReturn(reference());
        var scoped =
                new CdsExportDrugReferenceSourceReader(drugs, projection, lookup)
                        .forCapture(27, mock(CdsExportRunControl.class));
        assertThat(scoped.count(27)).isEqualTo(1);
        // An absent product is remembered like a found one: one lookup per distinct DIN.
        verify(lookup, times(1)).read("00999999");
        verify(lookup, times(1)).read("00123456");
        verifyNoMoreInteractions(lookup);
    }

    @Test
    void shouldRefuseAMismatchedProduct_beforeReadingPayload() throws Exception {
        var drugs = mock(DrugDao.class);
        var projection = mock(CdsExportDrugReferenceProjectionService.class);
        var lookup = mock(CdsExportDrugReferenceLookupService.class);
        when(projection.read(27)).thenReturn(List.of(input(51, 17)));
        var other =
                new CdsExportDrugReferenceDto(
                        1,
                        CdsExportDrugReferenceDto.AUTHORITY,
                        "00999999",
                        "Other product",
                        reference().components());
        when(lookup.read("00123456")).thenReturn(other);
        assertThatThrownBy(
                        () ->
                                new CdsExportDrugReferenceSourceReader(drugs, projection, lookup)
                                        .forCapture(27, mock(CdsExportRunControl.class)))
                .hasMessage("CDS_EXPORT_DRUG_REFERENCE_REQUIRES_REVIEW");
        verifyNoInteractions(drugs);
    }
}
