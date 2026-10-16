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
package io.github.carlos_emr.carlos.sms.viewmodel;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SmsQueuePages}: only the known page parameters are read, only plain page numbers are taken, and the
 * links and form fields built from it hold nothing but the period's value and page parameter names and
 * numbers.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("security")
@DisplayName("SMS queue pages")
class SmsQueuePagesUnitTest {

    private static SmsQueuePages parse(Map<String, String[]> parameters) {
        return SmsQueuePages.fromParameters(parameters::get);
    }

    @Test
    @DisplayName("should read each list's page from its own parameter")
    void shouldReadPages_fromTheirOwnParameters() {
        SmsQueuePages pages = parse(Map.of(
                "page_STUB_failed", new String[]{"2"},
                "page_VOIPMS_overdue", new String[]{"9999999"}));

        assertThat(pages.page(SmsProviderType.STUB, SmsQueueList.FAILED)).isEqualTo(2);
        assertThat(pages.page(SmsProviderType.VOIPMS, SmsQueueList.OVERDUE)).isEqualTo(9_999_999);
        assertThat(pages.page(SmsProviderType.STUB, SmsQueueList.OVERDUE)).isEqualTo(1);
        assertThat(pages.page(SmsProviderType.CLOUDLI, SmsQueueList.BLOCKED)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "1", "-1", "+2", "02", " 2", "2 ", "1.5", "2e1", "0x2", "two", "２",
            "10000000", "99999999999999999999", "2&page_STUB_stale=3", "2#x"})
    @DisplayName("should take anything but a plain page number from 2 up, with at most seven digits, as page 1")
    void shouldTakeFirstPage_forAnythingButAPlainPageNumber(String value) {
        SmsQueuePages pages = parse(Map.of("page_STUB_failed", new String[]{value}));

        assertThat(pages).isEqualTo(SmsQueuePages.FIRST);
        assertThat(pages.page(SmsProviderType.STUB, SmsQueueList.FAILED)).isEqualTo(1);
    }

    @Test
    @DisplayName("should take a parameter sent twice, or with no value, as page 1")
    void shouldTakeFirstPage_forRepeatedOrNullValues() {
        assertThat(parse(Map.of("page_STUB_failed", new String[]{"2", "3"}))).isEqualTo(SmsQueuePages.FIRST);
        assertThat(parse(Map.of("page_STUB_failed", new String[]{null}))).isEqualTo(SmsQueuePages.FIRST);
        assertThat(parse(Map.of("page_STUB_failed", new String[0]))).isEqualTo(SmsQueuePages.FIRST);
    }

    @Test
    @DisplayName("should ask only for the page parameters of known SMS providers and lists")
    void shouldReadOnlyKnownParameters_forEveryProviderAndList() {
        List<String> asked = new ArrayList<>();
        SmsQueuePages pages = SmsQueuePages.fromParameters(name -> {
            asked.add(name);
            return new String[]{"2"};
        });

        List<String> expected = new ArrayList<>();
        for (SmsProviderType provider : SmsProviderType.values()) {
            for (SmsQueueList list : SmsQueueList.values()) {
                expected.add("page_" + provider.name() + "_" + list.parameterValue());
            }
        }
        assertThat(asked).containsExactlyElementsOf(expected);
        assertThat(pages.parameters()).hasSize(expected.size());
    }

    @Test
    @DisplayName("should build the query with the period first, then the pages in provider and list order")
    void shouldBuildQuery_inFixedOrder() {
        SmsQueuePages pages = SmsQueuePages.FIRST
                .with(SmsProviderType.STUB, SmsQueueList.BLOCKED, 4)
                .with(SmsProviderType.VOIPMS, SmsQueueList.STALE, 2)
                .with(SmsProviderType.STUB, SmsQueueList.OVERDUE, 3);

        assertThat(pages.query(SmsQueueWindow.ALL_TIME))
                .isEqualTo("window=all&page_VOIPMS_stale=2&page_STUB_overdue=3&page_STUB_blocked=4");
        assertThat(SmsQueuePages.FIRST.query(SmsQueueWindow.LAST_30_DAYS)).isEqualTo("window=30d");
        assertThat(pages.toString()).isEqualTo("page_VOIPMS_stale=2 page_STUB_overdue=3 page_STUB_blocked=4");
    }

    @Test
    @DisplayName("should drop a list from the links when it goes back to page 1")
    void shouldDropList_whenItGoesBackToFirstPage() {
        SmsQueuePages pages = SmsQueuePages.FIRST.with(SmsProviderType.STUB, SmsQueueList.FAILED, 2);

        assertThat(pages.with(SmsProviderType.STUB, SmsQueueList.FAILED, 1)).isEqualTo(SmsQueuePages.FIRST);
        assertThat(pages.with(SmsProviderType.STUB, SmsQueueList.FAILED, 0)).isEqualTo(SmsQueuePages.FIRST);
        assertThat(pages.with(SmsProviderType.STUB, SmsQueueList.FAILED, 1).parameters()).isEmpty();
    }

    @Test
    @DisplayName("should keep only the lists the time period does not change when another period is chosen")
    void shouldKeepPages_ofListsOutsideTheTimePeriod() {
        SmsQueuePages pages = SmsQueuePages.FIRST
                .with(SmsProviderType.STUB, SmsQueueList.OVERDUE, 2)
                .with(SmsProviderType.STUB, SmsQueueList.STALE, 3)
                .with(SmsProviderType.STUB, SmsQueueList.FAILED, 4)
                .with(SmsProviderType.STUB, SmsQueueList.BLOCKED, 5);

        assertThat(pages.forOtherWindow().parameters()).containsExactly(
                new SmsQueueViewModel.Parameter("page_STUB_overdue", "2"),
                new SmsQueueViewModel.Parameter("page_STUB_stale", "3"));
    }

    @Test
    @DisplayName("should count a list's pages, with one page for an empty list")
    void shouldCountPages_forListLengths() {
        assertThat(SmsQueuePages.lastPage(0)).isEqualTo(1);
        assertThat(SmsQueuePages.lastPage(1)).isEqualTo(1);
        assertThat(SmsQueuePages.lastPage(20)).isEqualTo(1);
        assertThat(SmsQueuePages.lastPage(21)).isEqualTo(2);
        assertThat(SmsQueuePages.lastPage(45)).isEqualTo(3);
        assertThat(SmsQueuePages.lastPage(Long.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
        // The largest page a request can ask for still starts at a row an int can hold.
        assertThat((9_999_999L - 1) * SmsQueuePages.PAGE_SIZE).isLessThan(Integer.MAX_VALUE);
    }
}
