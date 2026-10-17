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
package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A Population Report statistic over a population of 0, which a site with nobody admitted to a
 * Service program has: 0%, not a division by zero (issue #4855).
 */
@Tag("unit")
@Tag("report")
@DisplayName("Population Report statistic")
class ReportStatisticUnitTest {

    private Locale defaultLocale;

    @BeforeEach
    void useEnglishPercentages() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.CANADA);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void shouldReportZeroPercent_whenThePopulationIsEmpty() {
        assertThatCode(() -> new ReportStatistic(0, 0)).doesNotThrowAnyException();
        ReportStatistic statistic = new ReportStatistic(0, 0);

        assertThat(statistic.getCount()).isZero();
        assertThat(statistic.getSize()).isZero();
        assertThat(statistic.getPercent()).isEqualTo("0%");
    }

    @Test
    void shouldKeepTheCount_whenThePopulationIsEmpty() {
        ReportStatistic statistic = new ReportStatistic(3, 0);

        assertThat(statistic.getCount()).isEqualTo(3);
        assertThat(statistic.getPercent()).isEqualTo("0%");
    }

    @Test
    void shouldReportTheShare_forANonEmptyPopulation() {
        ReportStatistic statistic = new ReportStatistic(1, 3);

        assertThat(statistic.getCount()).isEqualTo(1);
        assertThat(statistic.getSize()).isEqualTo(3);
        assertThat(statistic.getPercent()).isEqualTo("33.33%");
    }

    @Test
    void shouldReportZeroPercent_whenTheSizeIsSetToZero() {
        ReportStatistic statistic = new ReportStatistic(2, 4);
        statistic.setSize(0);

        assertThat(statistic.getPercent()).isEqualTo("0%");
    }

    @Test
    void shouldOrderByLabelThenShare_forTheSameLabel() {
        ReportStatistic empty = new ReportStatistic(0, 0);
        ReportStatistic half = new ReportStatistic(1, 2);
        ReportStatistic quarter = new ReportStatistic(1, 4);
        empty.setLabel("x");
        half.setLabel("x");
        quarter.setLabel("x");

        assertThat(empty).isLessThan(quarter);
        assertThat(quarter).isLessThan(half);
        assertThat(new ReportStatistic(2, 4)).isEqualByComparingTo(new ReportStatistic(1, 2));
    }
}
