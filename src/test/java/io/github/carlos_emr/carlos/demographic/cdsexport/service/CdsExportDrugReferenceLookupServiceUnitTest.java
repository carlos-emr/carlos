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
import io.github.carlos_emr.carlos.prescript.util.RxDrugRef;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Tag("unit")
@Tag("cds-export")
class CdsExportDrugReferenceLookupServiceUnitTest {
    static Hashtable<String, Object> product() {
        var component = new Hashtable<String, Object>();
        component.put("name", "Native ingredient");
        component.put("strength", "5");
        component.put("unit", "mg");
        var result = new Hashtable<String, Object>();
        result.put("regional_identifier", "00123456");
        result.put("product", "Native reference tablet");
        result.put("components", new Vector<>(List.of(component)));
        return result;
    }

    @AfterEach
    void clear() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldFetchOnlyThePublicDin_withoutSendingAnyPatientOrPrescriptionData() throws Exception {
        var client = mock(RxDrugRef.class);
        when(client.getDrugByDIN("00123456", Boolean.FALSE)).thenReturn(product());
        var value = new CdsExportDrugReferenceLookupService(client).read("00123456");
        assertThat(value.din()).isEqualTo("00123456");
        assertThat(value.components().getFirst().strength()).isEqualTo("5");
        verify(client).getDrugByDIN("00123456", Boolean.FALSE);
        verifyNoMoreInteractions(client);
    }

    @Test
    void shouldReportAMismatchedOrMalformedProductAndAnUnknownDin_asNoUsableReference()
            throws Exception {
        // DrugRef's answer is reference data, not the chart: an answer the reference cannot
        // describe held the prescription's patient (CDS_EXPORT_DRUG_REFERENCE_REQUIRES_REVIEW).
        // It is now no usable reference, and the prescription is exported unqualified, as for a
        // DIN DrugRef does not know.
        var negative = new Hashtable<String, Object>();
        negative.put("name", "Native ingredient");
        negative.put("strength", -5.0d);
        negative.put("unit", "mg");
        var unnamed = new Hashtable<String, Object>();
        unnamed.put("strength", "5");
        unnamed.put("unit", "mg");
        for (var change :
                List.of(
                        Map.<String, Object>of("regional_identifier", "99999999"),
                        Map.<String, Object>of("components", "bad"),
                        Map.<String, Object>of("product", 17),
                        Map.<String, Object>of("product", " "),
                        Map.<String, Object>of("components", new Vector<>(List.of(negative))),
                        Map.<String, Object>of("components", new Vector<>(List.of(unnamed))),
                        Map.<String, Object>of("components", new Vector<>(List.of("bad"))))) {
            var product = product();
            product.putAll(change);
            var client = mock(RxDrugRef.class);
            when(client.getDrugByDIN("00123456", Boolean.FALSE)).thenReturn(product);
            assertThat(new CdsExportDrugReferenceLookupService(client).read("00123456"))
                    .as(change.keySet().toString())
                    .isNull();
        }
        // A21: DrugRef answers a DIN it no longer holds with no product (RxDrugRef returns null);
        // that is reported as absent, so the prescription is exported unqualified, not held.
        var missing = mock(RxDrugRef.class);
        assertThat(new CdsExportDrugReferenceLookupService(missing).read("00123456")).isNull();
    }

    @Test
    void shouldReadEveryIngredient_ofAProductWithMoreThanSixtyFourOfThem() throws Exception {
        // A product with more than 64 active ingredients (some natural and homeopathic products
        // list well over a hundred) held its prescription's patient
        // (CDS_EXPORT_DRUG_REFERENCE_REQUIRES_REVIEW). The DPD bounds no such count.
        var product = product();
        var components = new Vector<Object>();
        for (int i = 0; i < 150; i++) {
            var component = new Hashtable<String, Object>();
            component.put("name", "Ingredient " + i);
            component.put("strength", Integer.toString(i + 1));
            component.put("unit", "mg");
            components.add(component);
        }
        product.put("components", components);
        var client = mock(RxDrugRef.class);
        when(client.getDrugByDIN("00123456", Boolean.FALSE)).thenReturn(product);
        var value = new CdsExportDrugReferenceLookupService(client).read("00123456");
        assertThat(value.components()).hasSize(150);
        assertThat(value.components().getFirst().strength()).isEqualTo("1");
    }

    @Test
    void shouldPublishTextStrengthsThatAreNumbers_withoutATrailingZero() throws Exception {
        // DrugRef holds the strength as a float and sends that float's text: "250.0" for 250 mg.
        var product = product();
        var components = new java.util.Vector<java.util.Hashtable<String, Object>>();
        var component = new java.util.Hashtable<String, Object>();
        component.put("name", "AMOXICILLIN");
        component.put("unit", "MG");
        component.put("strength", "250.0");
        components.add(component);
        product.put("components", components);
        var client = mock(RxDrugRef.class);
        when(client.getDrugByDIN("00123456", Boolean.FALSE)).thenReturn(product);

        var value = new CdsExportDrugReferenceLookupService(client).read("00123456");

        assertThat(value.components().getFirst().strength()).isEqualTo("250");
        assertThat(CdsExportDrugReferenceLookupService.strength("500.0")).isEqualTo("500");
        assertThat(CdsExportDrugReferenceLookupService.strength("1.0E7")).isEqualTo("10000000");
        assertThat(CdsExportDrugReferenceLookupService.strength("0.5")).isEqualTo("0.5");
        assertThat(CdsExportDrugReferenceLookupService.strength("5")).isEqualTo("5");
    }

    @Test
    void shouldPublishNumericStrengthsAsExactPlainDecimals_withoutATrailingZero() throws Exception {
        assertThat(CdsExportDrugReferenceLookupService.strength(500.0d)).isEqualTo("500");
        assertThat(CdsExportDrugReferenceLookupService.strength(1.0E7d)).isEqualTo("10000000");
        assertThat(CdsExportDrugReferenceLookupService.strength(0.125d)).isEqualTo("0.125");
        assertThat(CdsExportDrugReferenceLookupService.strength(0.0d)).isEqualTo("0");
        assertThat(CdsExportDrugReferenceLookupService.strength("2.5 mg/mL")).isEqualTo("2.5 mg/mL");
        // No ingredient has a negative or non-finite strength: no usable reference.
        assertThat(CdsExportDrugReferenceLookupService.strength(Double.NaN)).isNull();
        assertThat(CdsExportDrugReferenceLookupService.strength(-1.5d)).isNull();
    }

    @Test
    void shouldRefuseNetworkWorkInsideADatabaseTransaction_beforeCallingDrugRef() {
        var client = mock(RxDrugRef.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> new CdsExportDrugReferenceLookupService(client).read("00123456"))
                .hasMessage("CDS_EXPORT_REFERENCE_LOOKUP_INSIDE_TRANSACTION_REFUSED");
        verifyNoInteractions(client);
    }

    @Test
    void shouldSurfaceReferenceServiceFailures_withoutSourceExceptionMessages() throws Exception {
        var client = mock(RxDrugRef.class);
        when(client.getDrugByDIN("00123456", Boolean.FALSE))
                .thenThrow(new IllegalStateException("private endpoint"));
        assertThatThrownBy(() -> new CdsExportDrugReferenceLookupService(client).read("00123456"))
                .hasMessage("CDS_EXPORT_DRUG_REFERENCE_UNAVAILABLE")
                .hasNoCause();
    }
}
