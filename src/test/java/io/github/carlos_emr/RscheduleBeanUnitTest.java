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
package io.github.carlos_emr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.GregorianCalendar;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the null/empty provider_no contract on {@link RscheduleBean}
 * after the {@code !"".equals(provider_no)} → {@code StringUtils.isNotEmpty(...)}
 * change, which altered the null-handling semantics (previously null fell
 * through; now null short-circuits and returns an empty string).
 */
@Tag("unit")
@Tag("scheduling")
class RscheduleBeanUnitTest {

    private static final GregorianCalendar ANY_DATE = new GregorianCalendar(2026, 3, 12);

    @Test
    void shouldReadAndReplaceProviderRulesWhileACallerHoldsThePublicBeanMonitor() throws Exception {
        var rule = new RscheduleBean();
        rule.active = "D";
        try (var worker = Executors.newSingleThreadExecutor()) {
            synchronized (rule) {
                var result = worker.submit(() -> {
                    rule.setRscheduleBean("A", "2031-05-14", "2031-12-31", "A", "4", "4",
                            "<WED>1300-1400</WED>", "<WED>0800-0900</WED>", "FAKE");
                    var original = rule.snapshotForProvider("A");
                    rule.setRscheduleBean("B", "2031-05-14", "2031-12-31", "1", "4", "",
                            "<WED>1000-1100</WED>", "FAKE");
                    assertThat(rule.snapshotForProvider("A")).isNull();
                    assertThat(rule.snapshotForProvider(null)).isNull();
                    rule.clear();
                    assertThat(rule.snapshotForProvider("B")).isNull();
                    return original;
                });
                var original = result.get(15, TimeUnit.SECONDS);
                assertThat(original.active).isEqualTo("D");
                assertThat(original.getDateAvailHour("2031-05-14")).isEqualTo("0800-0900");
                assertThat(original.getDateAvailHour("2031-05-21")).isEqualTo("1300-1400");
            }
        }
    }

    @Test
    @DisplayName("getDateAvailHour should return empty string when provider_no is null")
    void shouldReturnEmpty_whenProviderNoIsNullOnGetDateAvailHour() {
        RscheduleBean bean = new RscheduleBean();
        bean.provider_no = null;

        assertThat(bean.getDateAvailHour(ANY_DATE)).isEmpty();
    }

    @Test
    @DisplayName("getDateAvailHour should return empty string when provider_no is empty")
    void shouldReturnEmpty_whenProviderNoIsEmptyOnGetDateAvailHour() {
        RscheduleBean bean = new RscheduleBean();
        bean.provider_no = "";

        assertThat(bean.getDateAvailHour(ANY_DATE)).isEmpty();
    }

    @Test
    @DisplayName("getSiteAvail should return empty string when provider_no is null")
    void shouldReturnEmpty_whenProviderNoIsNullOnGetSiteAvail() {
        RscheduleBean bean = new RscheduleBean();
        bean.provider_no = null;

        assertThat(bean.getSiteAvail(ANY_DATE)).isEmpty();
    }

    @Test
    @DisplayName("getSiteAvail should return empty string when provider_no is empty")
    void shouldReturnEmpty_whenProviderNoIsEmptyOnGetSiteAvail() {
        RscheduleBean bean = new RscheduleBean();
        bean.provider_no = "";

        assertThat(bean.getSiteAvail(ANY_DATE)).isEmpty();
    }
}
