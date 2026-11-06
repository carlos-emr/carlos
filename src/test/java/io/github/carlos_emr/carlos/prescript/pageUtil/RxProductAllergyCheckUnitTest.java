/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.prescript.data.RxDrugData;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The full prescription page must retain unchecked results and never reuse one DIN's ingredient verdict for another. */
@Tag("unit")
class RxProductAllergyCheckUnitTest extends CarlosUnitTestBase {
    @BeforeEach
    void setUp() {
        registerMock(io.github.carlos_emr.carlos.commn.dao.AllergyDao.class,
                mock(io.github.carlos_emr.carlos.commn.dao.AllergyDao.class));
        registerMock(io.github.carlos_emr.carlos.commn.dao.PartialDateDao.class,
                mock(io.github.carlos_emr.carlos.commn.dao.PartialDateDao.class));
    }

    @Test
    void shouldReturnWarningsAndUncheckedAllergies_forTheExactProduct() {
        var info = mock(LoggedInInfo.class);
        var patient = mock(RxPatientData.Patient.class);
        var allergy = new Allergy();
        when(patient.getActiveAllergies()).thenReturn(new Allergy[]{allergy});
        var bean = new RxSessionBean();
        bean.setDemographicNo(42);
        try (var patients = mockStatic(RxPatientData.class);
             var _ = mockConstruction(RxDrugData.class, (value, context) -> {
                 when(value.getAllergyWarnings(eq("S01CA02"), any(), anyList(), anyString())).thenAnswer(call -> {
                     List<Allergy> missing = call.getArgument(2);
                     if ("00000001".equals(call.getArgument(3))) return new Allergy[]{allergy};
                     missing.add(allergy);
                     return new Allergy[0];
                 });
             })) {
            patients.when(() -> RxPatientData.getPatient(info, 42)).thenReturn(patient);
            var first = bean.checkProductAllergies(info, "S01CA02", "00000001");
            var second = bean.checkProductAllergies(info, "S01CA02", "00000002");
            assertThat(first.warnings()).singleElement().isSameAs(allergy);
            assertThat(second.warnings()).isEmpty();
            assertThat(second.unchecked()).singleElement().isSameAs(allergy);
            assertThat(second.failed()).isFalse();
        }
    }

    @Test
    void shouldExposeFailure_whenTheAllergyListCannotBeLoaded() {
        var info = mock(LoggedInInfo.class);
        var bean = new RxSessionBean();
        bean.setDemographicNo(42);
        try (var patients = mockStatic(RxPatientData.class)) {
            patients.when(() -> RxPatientData.getPatient(info, 42)).thenThrow(new IllegalStateException());
            var result = bean.checkProductAllergies(info, "S01CA02", "00000001");
            assertThat(result.failed()).isTrue();
            assertThat(result.warnings()).isEmpty();
        }
    }

    @Test
    void shouldCheckNothing_whenAllergyCheckingIsTurnedOff() {
        var info = mock(LoggedInInfo.class);
        var bean = new RxSessionBean();
        bean.setDemographicNo(42);
        CarlosProperties properties = CarlosProperties.getInstance();
        String previous = properties.getProperty("RX_ALLERGY_CHECKING");
        properties.setProperty("RX_ALLERGY_CHECKING", "no");
        try (var patients = mockStatic(RxPatientData.class);
             var drugData = mockConstruction(RxDrugData.class)) {
            var result = bean.checkProductAllergies(info, "S01CA02", "00000001");
            assertThat(result.warnings()).isEmpty();
            assertThat(result.unchecked()).isEmpty();
            assertThat(result.failed()).isFalse();
            patients.verifyNoInteractions();
            assertThat(drugData.constructed()).isEmpty();
        } finally {
            if (previous == null) {
                properties.remove("RX_ALLERGY_CHECKING");
            } else {
                properties.setProperty("RX_ALLERGY_CHECKING", previous);
            }
        }
    }
}
