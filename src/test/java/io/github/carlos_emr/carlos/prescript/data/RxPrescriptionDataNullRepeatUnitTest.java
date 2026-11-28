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
    @org.junit.jupiter.api.Test
    @DisplayName("should keep invalid clinical duration values out of diagnostic exceptions")
    void shouldKeepDurationPrivate_whenRenderingMalformedLegacyInstructions() {
        var prescription = new RxPrescriptionData().newPrescription("999998", 817263);
        prescription.setDuration("PRIVATE_CLINICAL_DURATION");
        try (var logs = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(RxPrescriptionData.class)) {
            prescription.getRxDisplay();
            assertThat(logs.events()).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getMessage().getFormattedMessage()).doesNotContain("PRIVATE_CLINICAL", "817263");
                assertThat(event.getThrown()).isNull();
                assertThat(event.getMessage().getParameters()).containsExactly("NumberFormatException");
            });
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should load an available history item once without logging its identifier")
    void shouldLoadHistoryOnce_whenInstructionsAreMissing() {
        Drug drug = new Drug();
        drug.setId(918273);
        drug.setDemographicId(817263);
        drug.setProviderNo("999998");
        DrugDao dao = mock(DrugDao.class);
        when(dao.find(918273)).thenReturn(drug);
        registerMock(DrugDao.class, dao);
        try (var logs = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(RxPrescriptionData.class)) {
            var result = new RxPrescriptionData().getPrescriptionIfPresent(918273);
            assertThat(result.getDrugId()).isEqualTo(918273);
            assertThat(result.getDemographicNo()).isEqualTo(817263);
            verify(dao).find(918273);
            org.mockito.Mockito.verifyNoMoreInteractions(dao);
            assertThat(logs.messages()).isNotEmpty().allSatisfy(message ->
                    assertThat(message).doesNotContain("918273", "817263", "999998"));
            assertThat(logs.events()).allSatisfy(event -> {
                assertThat(event.getThrown()).isNull();
                Object[] parameters = event.getMessage().getParameters();
                if (parameters != null) assertThat(parameters).allSatisfy(parameter ->
                        assertThat(String.valueOf(parameter)).isEqualTo("0"));
            });
        }
    }

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

    /**
     * A new prescription for a drug starts from the quantity of the previous one. Only the first run
     * of digits was carried over, so a previous "2.5" (of 2.5 mL) became the default "2" (#6185).
     */
    @ParameterizedTest(name = "previous quantity {0} ({1}) -> {2} ({3})")
    @CsvSource(nullValues = "NULL", value = {
            "2.5,mL,2.5,mL", "0.5,mL,0.5,mL", "0.0625,mL,0.0625,mL", "30.0,mL,30,mL",
            "1/2,bottle,1/2,bottle", "2.5,NULL,2.5,NULL", "30.0,NULL,30,NULL",
            "2.5 mL,NULL,2.5,mL", "30.0 mL,NULL,30,mL",
            "1 bottle (120 mL),NULL,1,bottle (120 mL)", "1/2 bottle,NULL,1/2 bottle,NULL",
            "one bottle,NULL,one bottle,NULL"
    })
    @DisplayName("should carry the amount together with either form of the stored unit")
    void shouldCarryQuantityAndUnit_whenReusingPreviousPrescription(String quantity, String unitName,
                                                                    String expectedQuantity, String expectedUnitName) {
        Drug drug = new Drug();
        drug.setRepeat(0);
        drug.setQuantity(quantity);
        drug.setUnitName(unitName);
        drug.setSpecial("Take 2.5 mL daily");
        DrugDao dao = mock(DrugDao.class);
        when(dao.findByCustomNameDemographicIdAndProviderNo("Synthetic drug", 1, "999998")).thenReturn(drug);
        registerMock(DrugDao.class, dao);
        RxPrescriptionData.Prescription result = new RxPrescriptionData().newPrescription("999998", 1);
        result.setCustomName("Synthetic drug");
        result.setQuantity("30");

        RxUtil.setSpecialQuantityRepeat(result);

        assertThat(result.getQuantity()).isEqualTo(expectedQuantity);
        assertThat(result.getUnitName()).isEqualTo(expectedUnitName);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should trim embedded-unit Qty footer cleanly and avoid duplicating the unit in findDuration")
    void shouldTrimEmbeddedUnitFooterWithoutDuplicatingUnit_whenReusingPreviousPrescription() {
        Drug drug = new Drug();
        drug.setRepeat(0);
        drug.setQuantity("2.5 mL");
        drug.setUnitName(null);
        drug.setSpecial("AMOXICILLIN 250MG/5ML\nTake 2.5 mL PO BID\nQty:2.5 mL Repeats:0");
        DrugDao dao = mock(DrugDao.class);
        when(dao.findByCustomNameDemographicIdAndProviderNo("AMOXICILLIN 250MG/5ML", 1, "999998")).thenReturn(drug);
        registerMock(DrugDao.class, dao);
        RxPrescriptionData.Prescription result = new RxPrescriptionData().newPrescription("999998", 1);
        result.setCustomName("AMOXICILLIN 250MG/5ML");
        result.setQuantity("30");

        RxUtil.setSpecialQuantityRepeat(result);
        RxUtil.findDuration(result);

        assertThat(result.getSpecial()).isEqualTo("Take 2.5 mL PO BID");
        assertThat(result.getQuantity()).isEqualTo("2.5");
        assertThat(result.getUnitName()).isEqualTo("mL");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"NULL,0", "0,0", "3,3"})
    @DisplayName("should preserve repeat counts when saving a legacy drug as a favorite")
    void shouldPreserveRepeatCount_whenAddingLegacyDrugToFavorites(Integer repeats, int expected) {
        Drug drug = new Drug();
        drug.setRepeat(repeats);
        drug.setDispenseInternal(true);
        drug.setSpecial("Synthetic drug: one tablet daily");
        FavoriteDao dao = mock(FavoriteDao.class);
        registerMock(FavoriteDao.class, dao);
        doAnswer(invocation -> {
            Favorite saved = invocation.getArgument(0);
            assertThat(saved.getRepeat()).isEqualTo(expected);
            assertThat(saved.isDispenseInternal()).isTrue();
            assertThat(saved.getSpecial()).isEqualTo(drug.getSpecial());
            saved.setId(42);
            return null;
        }).when(dao).persist(any(Favorite.class));

        assertThat(RxPrescriptionData.addToFavorites("999998", "Synthetic favorite", drug)).isTrue();
        verify(dao).persist(any(Favorite.class));
        assertThat(drug.getRepeat()).isEqualTo(repeats);
    }
}
