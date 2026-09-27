/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.prescript.data;

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.FavoriteDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Favorite;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("prescription")
@DisplayName("Legacy prescription repeat defaults")
class RxPrescriptionDataNullRepeatUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"true,NULL,false", "false,NULL,false", "true,false,false", "false,false,false", "true,true,true", "false,true,true"})
    @DisplayName("should preserve internal dispensing flags and default missing legacy flags")
    void shouldPreserveInternalDispensing_whenLoadingLegacyDrug(boolean byId, Boolean flag, boolean expected) {
        Drug drug = new Drug();
        drug.setId(27);
        drug.setProviderNo("999998");
        drug.setDemographicId(1);
        drug.setSpecial("Synthetic drug: one tablet daily");
        drug.setDispenseInternal(flag);
        DrugDao dao = mock(DrugDao.class);
        when(dao.find(27)).thenReturn(drug);
        registerMock(DrugDao.class, dao);

        RxPrescriptionData data = new RxPrescriptionData();
        RxPrescriptionData.Prescription result = byId ? data.getPrescription(27) : data.toPrescription(drug, 1);

        assertThat(result.isDispenseInternal()).isEqualTo(expected);
        assertThat(drug.getDispenseInternal()).isEqualTo(flag);
    }

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

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"NULL,0,30,30", "0,0,30,30", "3,3,30,30", "NULL,0,NULL,30"})
    @DisplayName("should reuse available legacy instructions without unboxing absent repeats or parsing an absent quantity")
    void shouldPreserveAvailableInstructions_whenReusingLegacyDrug(Integer repeats, int expected, String quantity, String expectedQuantity) {
        Drug drug = new Drug();
        drug.setRepeat(repeats);
        drug.setQuantity(quantity);
        drug.setSpecial("One tablet daily");
        DrugDao dao = mock(DrugDao.class);
        when(dao.findByCustomNameDemographicIdAndProviderNo("Synthetic drug", 1, "999998")).thenReturn(drug);
        registerMock(DrugDao.class, dao);
        RxPrescriptionData.Prescription result = new RxPrescriptionData().newPrescription("999998", 1);
        result.setCustomName("Synthetic drug");
        result.setQuantity("30");

        RxUtil.setSpecialQuantityRepeat(result);

        assertThat(result.getRepeat()).isEqualTo(expected);
        assertThat(result.getQuantity()).isEqualTo(expectedQuantity);
        assertThat(result.getSpecial()).isEqualTo("One tablet daily");
        assertThat(drug.getRepeat()).isEqualTo(repeats);
        assertThat(drug.getQuantity()).isEqualTo(quantity);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"NULL,0", "0,0", "3,3"})
    @DisplayName("should preserve repeat counts when saving a legacy drug as a favorite")
    void shouldPreserveRepeatCount_whenAddingLegacyDrugToFavorites(Integer repeats, int expected) {
        Drug drug = new Drug();
        drug.setRepeat(repeats);
        drug.setSpecial("Synthetic drug: one tablet daily");
        FavoriteDao dao = mock(FavoriteDao.class);
        registerMock(FavoriteDao.class, dao);
        doAnswer(invocation -> {
            Favorite saved = invocation.getArgument(0);
            assertThat(saved.getRepeat()).isEqualTo(expected);
            assertThat(saved.getSpecial()).isEqualTo(drug.getSpecial());
            saved.setId(42);
            return null;
        }).when(dao).persist(any(Favorite.class));

        assertThat(RxPrescriptionData.addToFavorites("999998", "Synthetic favorite", drug)).isTrue();
        verify(dao).persist(any(Favorite.class));
        assertThat(drug.getRepeat()).isEqualTo(repeats);
    }
}
