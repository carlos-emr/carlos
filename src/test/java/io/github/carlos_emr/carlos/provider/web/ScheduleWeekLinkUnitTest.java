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

import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

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
 * for one provider; these tests pin that the month view now targets it, that it asks the
 * sheet for the provider the month shows (the sheet otherwise lists the viewer's saved
 * group and would render another provider's week), and that it links only when the sheet
 * can show that provider.
 *
 * @since 2026-10-14
 */
@Tag("unit")
@Tag("provider")
@DisplayName("Schedule month view week link")
class ScheduleWeekLinkUnitTest {

    private static final String CONTEXT = "/carlos";

    /** Providers that exist, by provider number, with the name the day sheet prints for them. */
    private static final Map<String, String> PROVIDERS = Map.of(
            "999998", "Doe, Jane",
            "100001", "Roe, Rick");

    private static final UnaryOperator<String> NAME_OF = PROVIDERS::get;

    /** The viewer has no MyGroupAccessRestriction rows. */
    private static final Predicate<String> NOT_RESTRICTED = providerNo -> false;

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
    @DisplayName("weekTarget")
    class WeekTarget {

        @Test
        @DisplayName("should resolve the provider and the name the day sheet prints")
        void shouldResolveProviderAndName_forExistingSingleProvider() {
            ScheduleWeekLink.Target target = ScheduleWeekLink.weekTarget("100001", ".default", NAME_OF, NOT_RESTRICTED);

            assertThat(target).isNotNull();
            assertThat(target.providerNo()).isEqualTo("100001");
            assertThat(target.providerName()).isEqualTo("Roe, Rick");
        }

        @Test
        @DisplayName("should return null when the month shows several providers")
        void shouldReturnNull_whenMonthShowsSeveralProviders() {
            assertThat(ScheduleWeekLink.weekTarget("all", ".default", NAME_OF, NOT_RESTRICTED)).isNull();
            assertThat(ScheduleWeekLink.weekTarget("_grp_7", ".default", NAME_OF, NOT_RESTRICTED)).isNull();
            assertThat(ScheduleWeekLink.weekTarget(null, ".default", NAME_OF, NOT_RESTRICTED)).isNull();
        }

        @Test
        @DisplayName("should return null when the provider does not exist")
        void shouldReturnNull_whenProviderDoesNotExist() {
            assertThat(ScheduleWeekLink.weekTarget("424242", ".default", NAME_OF, NOT_RESTRICTED)).isNull();
        }

        @Test
        @DisplayName("should return null when the viewer's saved view is pinned to another provider")
        void shouldReturnNull_whenSavedViewIsAnotherProvider() {
            // The day sheet shows the provider saved as the schedule view whatever the URL asks
            // for, so a link for a different provider would open the wrong provider's week.
            assertThat(ScheduleWeekLink.weekTarget("100001", "999998", NAME_OF, NOT_RESTRICTED)).isNull();
        }

        @Test
        @DisplayName("should resolve the provider when the saved view is that same provider")
        void shouldResolveProvider_whenSavedViewIsSameProvider() {
            assertThat(ScheduleWeekLink.weekTarget("999998", "999998", NAME_OF, NOT_RESTRICTED))
                    .extracting(ScheduleWeekLink.Target::providerNo)
                    .isEqualTo("999998");
        }

        @Test
        @DisplayName("should return null for a provider the viewer is restricted from")
        void shouldReturnNull_whenProviderIsRestrictedForViewer() {
            // The day sheet's schedule-view selector hides providers listed in the viewer's
            // MyGroupAccessRestriction rows; a week number must not open one the selector hides.
            assertThat(ScheduleWeekLink.weekTarget("100001", ".default", NAME_OF, "100001"::equals)).isNull();
        }

        @Test
        @DisplayName("should resolve the provider when only another provider is restricted")
        void shouldResolveProvider_whenOnlyAnotherProviderIsRestricted() {
            assertThat(ScheduleWeekLink.weekTarget("100001", ".default", NAME_OF, "999998"::equals)).isNotNull();
        }

        @Test
        @DisplayName("should resolve the provider when the saved view is a group, the default or unset")
        void shouldResolveProvider_whenSavedViewIsNotAProvider() {
            assertThat(ScheduleWeekLink.weekTarget("100001", "clinic-team", NAME_OF, NOT_RESTRICTED)).isNotNull();
            assertThat(ScheduleWeekLink.weekTarget("100001", ".default", NAME_OF, NOT_RESTRICTED)).isNotNull();
            assertThat(ScheduleWeekLink.weekTarget("100001", null, NAME_OF, NOT_RESTRICTED)).isNotNull();
        }
    }

    @Nested
    @DisplayName("href")
    class Href {

        private final ScheduleWeekLink.Target jane = new ScheduleWeekLink.Target("999998", "Doe, Jane");

        @Test
        @DisplayName("should open the provider alone on the day sheet in week view")
        void shouldTargetDaySheetWeekView_forSingleProvider() {
            String href = ScheduleWeekLink.href(CONTEXT, 2026, 10, 4, jane);

            // view=1 with curProvider/curProviderName is how the sheet is asked for one provider
            // (the zoom view); weekView=true/provider_no/viewall are the "W" button's parameters.
            assertThat(href).isEqualTo(
                    "/carlos/provider/providercontrol?year=2026&month=10&day=4"
                            + "&view=1&curProvider=999998&curProviderName=Doe%2C%20Jane"
                            + "&displaymode=day&dboperation=searchappointmentday&viewall=1"
                            + "&weekView=true&provider_no=999998");
        }

        @Test
        @DisplayName("should never use the retired week display mode")
        void shouldNotUseRetiredWeekMode_forAnyProvider() {
            String href = ScheduleWeekLink.href(CONTEXT, 2026, 10, 4, jane);

            assertThat(href)
                    .doesNotContain("displaymode=week")
                    .doesNotContain("searchapptweek");
        }

        @Test
        @DisplayName("should return null without a target")
        void shouldReturnNull_forMissingTarget() {
            assertThat(ScheduleWeekLink.href(CONTEXT, 2026, 10, 4, null)).isNull();
        }

        @Test
        @DisplayName("should encode the provider number and name as URL query components")
        void shouldEncodeProviderNumberAndName_inQueryString() {
            ScheduleWeekLink.Target hostile =
                    new ScheduleWeekLink.Target("9 9&weekView=false#x", "O'Brien & <b>Sons</b>");

            String href = ScheduleWeekLink.href(CONTEXT, 2026, 10, 4, hostile);

            assertThat(href)
                    .contains("&curProvider=9%209%26weekView%3Dfalse%23x&")
                    .endsWith("&weekView=true&provider_no=9%209%26weekView%3Dfalse%23x")
                    .contains("&curProviderName=O%27Brien%20%26%20%3Cb%3ESons%3C%2Fb%3E&")
                    .doesNotContain(" ")
                    .doesNotContain("#")
                    .doesNotContain("<");
        }

        @Test
        @DisplayName("should carry the given date without padding so the sheet can snap to its week")
        void shouldKeepGivenDate_withoutPadding() {
            String href = ScheduleWeekLink.href(CONTEXT, 2027, 1, 31, jane);

            assertThat(href).contains("year=2027&month=1&day=31&");
        }

        @Test
        @DisplayName("should build a root-relative link when the context path is empty")
        void shouldBuildRootRelativeLink_forRootContext() {
            assertThat(ScheduleWeekLink.href("", 2026, 10, 4, jane))
                    .startsWith("/provider/providercontrol?year=2026&month=10&day=4&");
            assertThat(ScheduleWeekLink.href(null, 2026, 10, 4, jane))
                    .startsWith("/provider/providercontrol?year=2026&month=10&day=4&");
        }
    }
}
