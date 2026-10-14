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
package io.github.carlos_emr.carlos.provider.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScheduleWeekLink}, which builds the link behind a week
 * number on the schedule month view (issue #4540).
 *
 * <p>The month view used to link each week number to
 * {@code displaymode=week&dboperation=searchapptweek}. That display mode has never been
 * in the {@code providercontrol.jsp} dispatch table, so every click answered the 404
 * error page. The week view that does exist is the day sheet with {@code weekView=true}
 * for one provider, the same page the "W" button on a provider column opens; these tests
 * pin that the month view now targets it, and only when the month shows one provider.
 *
 * @since 2026-10-14
 */
@Tag("unit")
@Tag("provider")
@DisplayName("Schedule month view week link")
class ScheduleWeekLinkUnitTest {

    private static final String CONTEXT = "/carlos";

    @Nested
    @DisplayName("singleProvider")
    class SingleProvider {

        @Test
        @DisplayName("should return the provider number when the month shows one provider")
        void shouldReturnProviderNumber_forPlainProviderView() {
            assertThat(ScheduleWeekLink.singleProvider("999998")).isEqualTo("999998");
        }

        @Test
        @DisplayName("should return null when the month shows every provider")
        void shouldReturnNull_forAllProvidersView() {
            assertThat(ScheduleWeekLink.singleProvider("all")).isNull();
        }

        @Test
        @DisplayName("should return null when the month shows a provider group")
        void shouldReturnNull_forGroupView() {
            assertThat(ScheduleWeekLink.singleProvider("_grp_12")).isNull();
            assertThat(ScheduleWeekLink.singleProvider("_grp_")).isNull();
        }

        @Test
        @DisplayName("should return null when no provider view is known")
        void shouldReturnNull_forMissingProviderView() {
            assertThat(ScheduleWeekLink.singleProvider(null)).isNull();
            assertThat(ScheduleWeekLink.singleProvider("")).isNull();
            assertThat(ScheduleWeekLink.singleProvider("   ")).isNull();
        }
    }

    @Nested
    @DisplayName("href")
    class Href {

        @Test
        @DisplayName("should target the day sheet week view for the provider")
        void shouldTargetDaySheetWeekView_forSingleProvider() {
            String href = ScheduleWeekLink.href(CONTEXT, 2026, 10, 4, "999998");

            // The same parameters the "W" button (goWeekView) sends from the day sheet.
            assertThat(href).isEqualTo(
                    "/carlos/provider/providercontrol?year=2026&month=10&day=4&view=0"
                            + "&displaymode=day&dboperation=searchappointmentday&viewall=1"
                            + "&weekView=true&provider_no=999998");
        }

        @Test
        @DisplayName("should never use the retired week display mode")
        void shouldNotUseRetiredWeekMode_forAnyProvider() {
            String href = ScheduleWeekLink.href(CONTEXT, 2026, 10, 4, "999998");

            assertThat(href)
                    .doesNotContain("displaymode=week")
                    .doesNotContain("searchapptweek");
        }

        @Test
        @DisplayName("should return null when the month shows several providers")
        void shouldReturnNull_whenMonthShowsSeveralProviders() {
            assertThat(ScheduleWeekLink.href(CONTEXT, 2026, 10, 4, "all")).isNull();
            assertThat(ScheduleWeekLink.href(CONTEXT, 2026, 10, 4, "_grp_7")).isNull();
            assertThat(ScheduleWeekLink.href(CONTEXT, 2026, 10, 4, null)).isNull();
        }

        @Test
        @DisplayName("should encode the provider number as a URL query component")
        void shouldEncodeProviderNumber_inQueryString() {
            String href = ScheduleWeekLink.href(CONTEXT, 2026, 10, 4, "9 9&weekView=false#x");

            assertThat(href)
                    .endsWith("&weekView=true&provider_no=9%209%26weekView%3Dfalse%23x")
                    .doesNotContain("provider_no=9 9")
                    .doesNotContain("#");
        }

        @Test
        @DisplayName("should carry the first day of the week row so the sheet can snap to its week")
        void shouldKeepGivenDate_withoutPadding() {
            String href = ScheduleWeekLink.href(CONTEXT, 2027, 1, 31, "100001");

            assertThat(href).contains("year=2027&month=1&day=31&");
        }

        @Test
        @DisplayName("should build a root-relative link when the context path is empty")
        void shouldBuildRootRelativeLink_forRootContext() {
            String href = ScheduleWeekLink.href("", 2026, 10, 4, "999998");

            assertThat(href).startsWith("/provider/providercontrol?year=2026&month=10&day=4&");
        }
    }
}
