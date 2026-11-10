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
package io.github.carlos_emr.carlos.webserv.rest.conversion.summary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.commn.model.CdsLegacyProjectionException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsMedicationCompatibilityException;
import io.github.carlos_emr.carlos.managers.PrescriptionManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.ws.rs.WebApplicationException;
import java.sql.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@Tag("unit")
class RxSummaryUnitTest {
    @Test void shouldRefuseCurrentImportedNullEndDate_andNeverReturnAPartialStartAnchor() {
        var manager=mock(PrescriptionManager.class);
        var projection=mock(CdsLegacyProjectionService.class);
        var drug=new Drug();drug.setId(7);drug.setCdsCurrentness("CURRENT");
        drug.setRxDate(Date.valueOf("2024-01-01"));drug.setStartDateUnknown(true);drug.setEndDate(null);
        var user=new LoggedInInfo();when(manager.getUniqueDrugsByPatient(user,11)).thenReturn(List.of(drug));
        var summary=new RxSummary(manager,projection);
        assertThatThrownBy(() -> summary.getSummary(user,11,"meds")).isInstanceOfSatisfying(WebApplicationException.class,failure -> {
            assertThat(failure.getResponse().getStatus()).isEqualTo(409);
            assertThat(failure.getResponse().getEntity().toString())
                    .contains(CdsMedicationCompatibilityException.CODE,"/demographic/cdsSourceFields?demographicNo=11&category=MedicationsAndTreatments")
                    .doesNotContain("2024-01-01");
            assertThat(failure.getResponse().getHeaderString("Cache-Control")).isEqualTo("no-store");
        });
    }

    @Test void shouldRefuseSourceOnlyOrUnknownMedicationRecords_beforeTheUniqueListCanOmitThem() {
        var manager=mock(PrescriptionManager.class);var projection=mock(CdsLegacyProjectionService.class);
        doThrow(new CdsLegacyProjectionException()).when(projection).requireSupported(11,Set.of("MedicationsAndTreatments"));
        var summary=new RxSummary(manager,projection);
        assertThatThrownBy(() -> summary.getSummary(new LoggedInInfo(),11,"meds"))
                .isInstanceOfSatisfying(WebApplicationException.class,failure -> assertThat(failure.getResponse().getStatus()).isEqualTo(409));
        verifyNoInteractions(manager);
    }

    @Test void shouldPreserveTheNativeDateApiAndSummary_whenNoImportedMedicationExists() {
        var manager=mock(PrescriptionManager.class);var projection=mock(CdsLegacyProjectionService.class);
        var drug=new Drug();drug.setId(8);drug.setRxDate(Date.valueOf("2024-01-01"));
        drug.setEndDate(Date.valueOf("2099-12-31"));drug.setSpecial("Native medication 1 tablet daily");
        var user=new LoggedInInfo();when(manager.getUniqueDrugsByPatient(user,11)).thenReturn(List.of(drug));
        var summary=new RxSummary(manager,projection);
        var result=summary.getSummary(user,11,"meds");
        assertThat(result.getSummaryItem()).hasSize(1);
        assertThat(result.getSummaryItem().getFirst().getDate()).isEqualTo(drug.getRxDate());
        verify(projection).requireSupported(11,Set.of("MedicationsAndTreatments"));
    }

    @Test void shouldWireTheRequiredCapabilityGuard_inTheActualSpringSummaryBean() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(PrescriptionManager.class,() -> mock(PrescriptionManager.class));
            context.registerBean(CdsLegacyProjectionService.class,() -> mock(CdsLegacyProjectionService.class));
            context.registerBean(RxSummary.class);context.refresh();
            assertThat(context.getBean(RxSummary.class).getSummary(new LoggedInInfo(),11,"meds").getSummaryItem()).isEmpty();
        }
    }
}
