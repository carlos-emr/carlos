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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsLegacyProjectionDao;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsLegacyProjectionException;
import jakarta.ws.rs.WebApplicationException;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsLegacyProjectionServiceUnitTest {
    private final CdsLegacyProjectionDao records=mock(CdsLegacyProjectionDao.class);
    private final CdsLegacyProjectionService service=new CdsLegacyProjectionService(records);

    @Test
    void shouldKeepExistingProjectionAvailable_whenPatientHasNoImportedRepresentation() {
        assertThatCode(()->service.requireWholeChartSupported(51)).doesNotThrowAnyException();
        assertThatCode(()->service.requireSupported(51,Set.of("Immunizations"))).doesNotThrowAnyException();
    }
    @Test
    void shouldRefuseCompleteChartProjection_whenStructuredSourceWouldBeOmitted() {
        when(records.hasSourceFields(51,Set.of())).thenReturn(true);
        assertThatThrownBy(()->service.requireWholeChartSupported(51)).isInstanceOf(CdsLegacyProjectionException.class);
    }
    @Test
    void shouldRefuseTypedLaboratoryProjection_evenIfSourceFieldRowsAreMissing() {
        when(records.hasTypedLaboratoryResults(51)).thenReturn(true);
        assertThatThrownBy(()->service.requireSupported(51,Set.of("LaboratoryResults")))
                .isInstanceOf(CdsLegacyProjectionException.class);
        assertThatThrownBy(()->service.requireWholeChartSupported(51)).isInstanceOf(CdsLegacyProjectionException.class);
        assertThatCode(()->service.requireSupported(51,Set.of("ClinicalNotes"))).doesNotThrowAnyException();
    }
    @Test
    void shouldFaultAnUnscopedFeed_whenNativeEmptyResultsCannotProveNoSourceOnlyRecords() {
        when(records.hasAnySourceFields(Set.of("CareElements"))).thenReturn(true);
        assertThatThrownBy(()->service.requireSoapFeedSupported(Set.of("CareElements")))
                .isInstanceOf(org.apache.cxf.binding.soap.SoapFault.class).hasMessageContaining("cannot represent");
    }
    @Test
    void shouldReturnExplicitSafeConflict_whenRestCannotRepresentImportedCategory() {
        when(records.hasSourceFields(51,Set.of("Immunizations"))).thenReturn(true);
        var refusal=catchThrowableOfType(()->service.requireRestSupported(51,Set.of("Immunizations")),WebApplicationException.class);
        assertThat(refusal.getResponse().getStatus()).isEqualTo(409);
        assertThat(refusal.getResponse().getHeaderString("Cache-Control")).isEqualTo("no-store");
        assertThat(refusal.getResponse().getEntity().toString()).contains("cannot represent", "clinical source view").doesNotContain("51");
    }
}
