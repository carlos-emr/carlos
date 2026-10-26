/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.prescript.data;

import io.github.carlos_emr.carlos.commn.dao.FavoriteDao;
import io.github.carlos_emr.carlos.commn.model.Favorite;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Existing favorite edits retain their selected identity, including stale-selection failures. */
@Tag("unit")
@Tag("prescription")
class RxFavoriteSaveUnitTest extends CarlosUnitTestBase {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Take one tablet twice daily"})
    void shouldStoreOptionalInstructions_whenAddingStagedDrug(String instructions) {
        FavoriteDao dao = mock(FavoriteDao.class);
        registerMock(FavoriteDao.class, dao);
        doAnswer(invocation -> {
            Favorite saved = invocation.getArgument(0);
            saved.setId(42);
            return null;
        }).when(dao).persist(any(Favorite.class));
        var prescription = new RxPrescriptionData().newPrescription("999998", 1);
        prescription.setCustomName("Synthetic custom drug");
        prescription.setSpecial(instructions);

        assertThat(prescription.AddToFavorites("999998", "Synthetic favorite")).isTrue();

        String expected = instructions == null ? "" : instructions;
        verify(dao).findDuplicate(argThat(favorite -> expected.equals(favorite.getSpecial())));
        verify(dao).persist(argThat((Favorite favorite) -> expected.equals(favorite.getSpecial())
                && "Synthetic custom drug".equals(favorite.getCustomName())));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void shouldPreserveDispensingFlag_whenLoadingSavingAndUsingFavorite(boolean dispenseInternal) {
        FavoriteDao dao = mock(FavoriteDao.class);
        registerMock(FavoriteDao.class, dao);
        Favorite stored = new Favorite();
        stored.setId(42);
        stored.setProviderNo("999998");
        stored.setSpecial("Synthetic instructions");
        stored.setDispenseInternal(dispenseInternal);
        when(dao.find(42)).thenReturn(stored);
        var data = new RxPrescriptionData();
        var favorite = data.getFavorite(42, "999998");

        assertThat(favorite.getDispenseInternal()).isEqualTo(dispenseInternal);
        assertThat(data.newPrescription("999998", 1, favorite).isDispenseInternal()).isEqualTo(dispenseInternal);
        assertThat(favorite.Save()).isTrue();
        assertThat(stored.isDispenseInternal()).isEqualTo(dispenseInternal);
        favorite.setDispenseInternal(!dispenseInternal);
        assertThat(favorite.Save()).isTrue();
        assertThat(stored.isDispenseInternal()).isEqualTo(!dispenseInternal);
    }

    @Test
    void shouldUpdateSelectedIdentity_whenSavingExistingFavorite() {
        FavoriteDao dao = mock(FavoriteDao.class);
        registerMock(FavoriteDao.class, dao);
        Favorite stored = new Favorite();
        stored.setId(42);
        stored.setProviderNo("999998");
        stored.setName("Original");
        when(dao.find(42)).thenReturn(stored);
        var favorite = new RxPrescriptionData().getFavorite(42, "999998");
        favorite.setFavoriteName("Replacement");
        favorite.setSpecial("Synthetic instructions");
        clearInvocations(dao);

        assertThat(favorite.Save()).isTrue();

        assertThat(stored.getName()).isEqualTo("Replacement");
        verify(dao).find(42);
        verify(dao).merge(stored);
        verifyNoMoreInteractions(dao);
    }

    @Test
    void shouldRejectStaleSelection_withoutUpdatingAnotherFavorite() {
        FavoriteDao dao = mock(FavoriteDao.class);
        registerMock(FavoriteDao.class, dao);
        Favorite stored = new Favorite();
        stored.setId(42);
        stored.setProviderNo("999998");
        stored.setSpecial("Synthetic instructions");
        when(dao.find(42)).thenReturn(stored, null);
        var favorite = new RxPrescriptionData().getFavorite(42, "999998");
        clearInvocations(dao);

        assertThatThrownBy(favorite::Save).isInstanceOf(IllegalStateException.class)
                .hasMessage("Prescription favorite is unavailable");
        verify(dao).find(42);
        verifyNoMoreInteractions(dao);
    }
}
