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

import java.util.List;
import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("prescription")
class RxPreviewSnapshotUnitTest extends CarlosUnitTestBase {
    private PrescriptionDao dao;

    @BeforeEach
    void setUp() {
        dao = mock(PrescriptionDao.class);
        registerMock(PrescriptionDao.class, dao);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "1x", " 1", "2147483648", "99999999999"})
    void shouldRefuseBeforeDatabaseRead_whenIdentityIsMalformed(String id) {
        assertThatIllegalArgumentException().isThrownBy(() -> RxPreviewSnapshot.load(42, id));
        verifyNoInteractions(dao);
    }

    @Test
    void shouldRefuseWithoutReadingClinicalData_whenScriptIsForeignOrMissing() {
        Prescription foreign = new Prescription();
        foreign.setDemographicId(43);
        foreign.setComments("private foreign notes");
        when(dao.find(7)).thenReturn(foreign);
        try (var data = mockConstruction(RxPrescriptionData.class)) {
            assertThat(RxPreviewSnapshot.load(42, "7")).isNull();
            assertThat(RxPreviewSnapshot.load(42, "8")).isNull();
            assertThat(data.constructed()).isEmpty();
        }
    }

    @Test
    void shouldKeepAllRowsAndComment_whenLoadingIndependentSnapshots() {
        Prescription header = new Prescription();
        header.setDemographicId(42);
        header.setProviderNo("doctor");
        header.setComments("saved notes");
        when(dao.find(7)).thenReturn(header);
        var first = mock(RxPrescriptionData.Prescription.class);
        var second = mock(RxPrescriptionData.Prescription.class);
        try (var data = mockConstruction(RxPrescriptionData.class, (mock, context) ->
                when(mock.getPrescriptionsByScriptNo(7, 42)).thenReturn(List.of(first, second)))) {
            RxPreviewSnapshot snapshot = RxPreviewSnapshot.load(42, "007");
            assertThat(snapshot.scriptId()).isEqualTo("7");
            assertThat(snapshot.comment()).isEqualTo("saved notes");
            assertThat(snapshot.bean().getDemographicNo()).isEqualTo(42);
            assertThat(snapshot.bean().getProviderNo()).isEqualTo("doctor");
            assertThat(snapshot.bean().getStashList()).containsExactly(first, second);
            RxPreviewSnapshot other = RxPreviewSnapshot.load(42, "7");
            snapshot.bean().getStashList().clear();
            assertThat(other.bean().getStashList()).containsExactly(first, second);
            verifyNoInteractions(first, second);
        }
    }

    @Test
    void shouldRefuseHeader_whenPatientOwnedDrugRowsAreMissing() {
        Prescription header = new Prescription();
        header.setDemographicId(42);
        when(dao.find(7)).thenReturn(header);
        try (var data = mockConstruction(RxPrescriptionData.class)) {
            assertThat(RxPreviewSnapshot.load(42, "7")).isNull();
        }
    }
}
