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
    void shouldRefuseUnknownMismatchedAndMalformedPublicProducts_insteadOfOmittingMandatoryDin()
            throws Exception {
        for (var change :
                List.of(
                        Map.of("regional_identifier", "99999999"),
                        Map.of("components", "bad"),
                        Map.of("product", 17))) {
            var product = product();
            product.putAll(change);
            var client = mock(RxDrugRef.class);
            when(client.getDrugByDIN("00123456", Boolean.FALSE)).thenReturn(product);
            assertThatThrownBy(
                            () -> new CdsExportDrugReferenceLookupService(client).read("00123456"))
                    .hasMessage("CDS_EXPORT_DRUG_REFERENCE_REQUIRES_REVIEW");
        }
        var missing = mock(RxDrugRef.class);
        assertThatThrownBy(() -> new CdsExportDrugReferenceLookupService(missing).read("00123456"))
                .hasMessage("CDS_EXPORT_DRUG_REFERENCE_REQUIRES_REVIEW");
    }

    @Test
    void shouldPublishNumericStrengthsAsExactPlainDecimals_withoutATrailingZero() throws Exception {
        assertThat(CdsExportDrugReferenceLookupService.strength(500.0d)).isEqualTo("500");
        assertThat(CdsExportDrugReferenceLookupService.strength(1.0E7d)).isEqualTo("10000000");
        assertThat(CdsExportDrugReferenceLookupService.strength(0.125d)).isEqualTo("0.125");
        assertThat(CdsExportDrugReferenceLookupService.strength(0.0d)).isEqualTo("0");
        assertThat(CdsExportDrugReferenceLookupService.strength("2.5 mg/mL")).isEqualTo("2.5 mg/mL");
        assertThatThrownBy(() -> CdsExportDrugReferenceLookupService.strength(Double.NaN))
                .hasMessage("CDS_EXPORT_DRUG_REFERENCE_REQUIRES_REVIEW");
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
