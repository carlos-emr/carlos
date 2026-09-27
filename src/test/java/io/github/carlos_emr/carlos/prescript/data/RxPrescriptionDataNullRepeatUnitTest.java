/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.prescript.data;

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("prescription")
@DisplayName("Legacy prescription repeat defaults")
class RxPrescriptionDataNullRepeatUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"true,NULL,0", "false,NULL,0", "true,0,0", "false,0,0", "true,3,3", "false,3,3"})
    @DisplayName("should retain the repeat count or use zero when the database has no count")
    void shouldPreserveRepeatCount_whenLoadingLegacyDrug(boolean byId, Integer repeats, int expected) {
        Drug drug = new Drug();
        drug.setId(27);
        drug.setDemographicId(1);
        drug.setProviderNo("999998");
        drug.setRepeat(repeats);
        drug.setSpecial("Synthetic drug: one tablet daily");
        DrugDao dao = mock(DrugDao.class);
        when(dao.find(27)).thenReturn(drug);
        registerMock(DrugDao.class, dao);
        RxPrescriptionData data = new RxPrescriptionData();
        RxPrescriptionData.Prescription result = byId ? data.getPrescription(27) : data.toPrescription(drug, 1);
        assertThat(result.getRepeat()).isEqualTo(expected);
        assertThat(result.getSpecial()).isEqualTo("Synthetic drug: one tablet daily");
        assertThat(drug.getRepeat()).isEqualTo(repeats);
    }
}
