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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.util.List;

import io.github.carlos_emr.carlos.billing.CA.dao.GstControlDao;
import io.github.carlos_emr.carlos.billing.CA.model.GstControl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the typed BigDecimal API extracted in commit {@code e38d36813b} —
 * the prior {@code Properties}-bag surface forced every caller to
 * string-fumble a numeric setting; the new accessors are typed so a
 * regression that re-introduces the Properties bag fails this suite.
 */
@DisplayName("GstSettingsService")
@Tag("unit")
@Tag("billing")
class GstSettingsServiceUnitTest {

    private GstControlDao dao;
    private GstSettingsService service;

    @BeforeEach
    void setUp() {
        dao = mock(GstControlDao.class);
        service = new GstSettingsService(dao);
    }

    @Test
    void shouldReturnPercent_fromFirstRow_whenTableHasOneRow() {
        GstControl row = new GstControl();
        row.setGstPercent(new BigDecimal("5.00"));
        when(dao.findAll()).thenReturn(List.of(row));

        assertThat(service.getCurrentPercent()).isEqualByComparingTo("5.00");
    }

    @Test
    void shouldReturnNull_whenTableIsEmpty() {
        when(dao.findAll()).thenReturn(List.of());

        assertThat(service.getCurrentPercent()).isNull();
    }

    @Test
    void shouldReturnPercent_fromFirstRow_whenTableHasMultipleRows() {
        // The single-row table is the operational shape, but if the table
        // somehow holds multiple rows the read side returns the first.
        GstControl first = new GstControl();
        first.setGstPercent(new BigDecimal("5.00"));
        GstControl second = new GstControl();
        second.setGstPercent(new BigDecimal("13.00"));
        when(dao.findAll()).thenReturn(List.of(first, second));

        assertThat(service.getCurrentPercent()).isEqualByComparingTo("5.00");
    }

    @Test
    void shouldUpdateAllRows_whenSettingPercent() {
        GstControl row1 = new GstControl();
        row1.setGstPercent(new BigDecimal("5.00"));
        GstControl row2 = new GstControl();
        row2.setGstPercent(new BigDecimal("13.00"));
        when(dao.findAll()).thenReturn(List.of(row1, row2));

        service.setCurrentPercent(new BigDecimal("7.50"));

        assertThat(row1.getGstPercent()).isEqualByComparingTo("7.50");
        assertThat(row2.getGstPercent()).isEqualByComparingTo("7.50");
        verify(dao, times(2)).merge(any(GstControl.class));
    }

    @Test
    void shouldNotMerge_whenTableIsEmpty() {
        when(dao.findAll()).thenReturn(List.of());

        service.setCurrentPercent(new BigDecimal("5.00"));

        verify(dao, never()).merge(any(GstControl.class));
    }
}
