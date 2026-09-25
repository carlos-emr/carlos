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
package io.github.carlos_emr.carlos.prescript.data;

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/** Ownership is checked before converting persisted drugs into request-scoped prescription DTOs. */
@Tag("unit")
@Tag("prescription")
class RxPrescriptionPatientIsolationUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource({"1001,1001,true", "2002,2002,false", "1001,2002,false", "2002,1001,false", ",1001,false", "1001,,false"})
    void shouldOnlyConvertRowsOwnedByRequestedPatient(Integer drugPatient, Integer scriptPatient, boolean expected) {
        DrugDao dao = mock(DrugDao.class);
        registerMock(DrugDao.class, dao);
        Drug drug = new Drug();
        drug.setId(55);
        drug.setScriptNo(77);
        drug.setDemographicId(drugPatient);
        drug.setBrandName("Stored medication");
        Prescription script = new Prescription();
        script.setDemographicId(scriptPatient);
        // Even inconsistent results must be checked before toPrescription overwrites demographicNo.
        when(dao.findDrugsAndPrescriptionsByScriptNumber(77, 1001))
                .thenReturn(Collections.singletonList(new Object[] {drug, script}));

        var results = new RxPrescriptionData().getPrescriptionsByScriptNo(77, 1001);

        assertThat(results).hasSize(expected ? 1 : 0);
        if (expected) {
            assertThat(results.getFirst().getDemographicNo()).isEqualTo(1001);
            assertThat(results.getFirst().getDrugId()).isEqualTo(55);
            assertThat(results.getFirst().getBrandName()).isEqualTo("Stored medication");
        }
        verify(dao).findDrugsAndPrescriptionsByScriptNumber(77, 1001);
    }
}
