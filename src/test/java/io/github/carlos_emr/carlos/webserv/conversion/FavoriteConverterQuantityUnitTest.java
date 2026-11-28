/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.webserv.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.commn.model.Favorite;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.FavoriteConverterImpl;

/**
 * A favourite saved from a prescription keeps that prescription's quantity as text. A quantity of
 * 2.5 (mL) is "2.5", which the transfer object's {@code Integer} cannot carry;
 * {@code Integer.parseInt} threw on it and failed the read of every favourite (#6185).
 *
 * @since 2026-11-26
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("FavoriteConverter stored quantity")
class FavoriteConverterQuantityUnitTest {

    private static Favorite favoriteWithQuantity(String quantity) {
        Favorite favorite = new Favorite();
        favorite.setProviderNo("999998");
        favorite.setName("Synthetic favourite");
        favorite.setDuration("7");
        favorite.setQuantity(quantity);
        return favorite;
    }

    @ParameterizedTest(name = "stored quantity \"{0}\" -> {1}")
    @CsvSource({"30, 30", "0, 0", "30.0, 30"})
    @DisplayName("should report a whole stored quantity")
    void shouldReportQuantity_whenStoredQuantityIsWhole(String stored, int expected) {
        Favorite favorite = favoriteWithQuantity(stored);

        assertThat(new FavoriteConverterImpl().getAsTransferObject(new LoggedInInfo(), favorite).getQuantity())
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "stored quantity \"{0}\"")
    @ValueSource(strings = {"2.5", "0.5", "100 mL"})
    @DisplayName("should omit a quantity the whole-number field cannot carry rather than fail the read")
    void shouldOmitQuantity_whenStoredQuantityIsNotWhole(String stored) {
        Favorite favorite = favoriteWithQuantity(stored);

        assertThat(new FavoriteConverterImpl().getAsTransferObject(new LoggedInInfo(), favorite).getQuantity())
                .isNull();
    }

    @ParameterizedTest(name = "stored duration \"{0}\" -> {1}")
    @CsvSource(nullValues = "NULL", value = {"NULL, NULL", "'', NULL", "abc, NULL", "7, 7", "7.0, 7"})
    @DisplayName("should omit a missing or non-whole duration rather than failing the favourite read")
    void shouldHandleNullableOrNonWholeDuration_whenConvertingFavorite(String duration, Integer expected) {
        Favorite favorite = favoriteWithQuantity("2.5");
        favorite.setDuration(duration);

        var dto = new FavoriteConverterImpl().getAsTransferObject(new LoggedInInfo(), favorite);

        assertThat(dto.getDuration()).isEqualTo(expected);
        assertThat(dto.getQuantity()).isNull();
    }
}
