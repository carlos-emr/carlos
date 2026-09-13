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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Consultations navbar module reads the request util's parallel lists cell by cell.
 * A consultation request saved without a status or date produces a null ELEMENT in a
 * non-empty list, which used to reach {@code status.equals("4")} and fail the whole
 * panel with an NPE (HTTP 500 on {@code encounter/displayConsultation}).
 */
@DisplayName("EctDisplayConsult2Action - parallel-list cell reads")
@Tag("unit")
@Tag("fast")
@Tag("encounter")
class EctDisplayConsult2ActionTest {

    @Test
    @DisplayName("A null element in a non-empty status list renders as empty, not as an NPE")
    void shouldReturnEmpty_whenStatusElementIsNull() {
        List<String> status = Arrays.asList("1", null, "4");

        assertThat(EctDisplayConsult2Action.columnOrEmpty(status, 1)).isEmpty();
        assertThat(EctDisplayConsult2Action.columnOrEmpty(status, 2)).isEqualTo("4");
    }

    @Test
    @DisplayName("An absent or empty list renders as empty")
    void shouldReturnEmpty_whenListIsNullOrEmpty() {
        assertThat(EctDisplayConsult2Action.columnOrEmpty(null, 0)).isEmpty();
        assertThat(EctDisplayConsult2Action.columnOrEmpty(Collections.emptyList(), 0)).isEmpty();
    }

    @Test
    @DisplayName("A slot past the end of the list renders as empty instead of throwing")
    void shouldReturnEmpty_whenIndexIsOutOfRange() {
        assertThat(EctDisplayConsult2Action.columnOrEmpty(Collections.singletonList("2"), 1)).isEmpty();
        assertThat(EctDisplayConsult2Action.columnOrEmpty(Collections.singletonList("2"), -1)).isEmpty();
    }

    @Test
    @DisplayName("Surrounding whitespace is trimmed so the cut-off comparison sees the bare code")
    void shouldTrimValue_whenElementHasWhitespace() {
        assertThat(EctDisplayConsult2Action.columnOrEmpty(Collections.singletonList(" 4 "), 0)).isEqualTo("4");
    }
}
