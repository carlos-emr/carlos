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
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.schedule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the discoverable schedule entry point and the provider availability view.
 *
 * <p>These are source-level checks because the subjects are JSPs with no extractable
 * seam. That makes them prone to becoming change-detectors, so each assertion here is
 * chosen to encode an invariant that has a user-visible or security consequence when
 * broken — not to pin an implementation. Prefer adding a case that would catch a real
 * regression over one that merely restates how the current code is written.
 *
 * @since 2026-07-30
 */
@DisplayName("Provider availability view JSP regressions")
@Tag("unit")
@Tag("schedule")
class ScheduleAvailabilityViewJspRegressionTest {

    private static final String INFIRMARY_GUARD = "<c:if test=\"${infirmaryView_isOscar != 'false'}\">";
    private static final String MOBILE_ROW = "<tr class=\"provider-availability-mobile-row";

    private static final Path PROVIDER_DAY_JSP =
            projectRoot().resolve("src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp");
    private static final Path AVAILABILITY_JSP =
            projectRoot().resolve("src/main/webapp/WEB-INF/jsp/schedule/scheduleflipview.jsp");
    private static final Path AVAILABILITY_CSS =
            projectRoot().resolve("src/main/webapp/css/scheduleavailability.css");
    private static final Path PROVIDER_DAY_CSS =
            projectRoot().resolve("src/main/webapp/css/receptionistapptstyle.css");
    private static final Path PROVIDER_DAY_MOBILE_CSS =
            projectRoot().resolve("src/main/webapp/mobile/receptionistapptstyle.css");

    /**
     * The entry point was an unlabelled radio input with a double-click handler, which was
     * undiscoverable and unreachable by keyboard. Issue #3254.
     */
    @Test
    @DisplayName("provider header should expose availability as a labelled link, not a bare radio")
    void shouldExposeAvailabilityLink_insteadOfUnlabelledRadio() throws IOException {
        String jsp = Files.readString(PROVIDER_DAY_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("provider-availability-link")
                .contains("provider.appointmentProviderAdminDay.viewAvailability")
                .contains("/schedule/FlipView?originalpage=")
                .doesNotContain("name='flipview'")
                .doesNotContain("goFilpView");
    }

    /**
     * The mobile row duplicates a desktop link that the CAISI infirmary view deliberately
     * suppresses. Both entry points must sit behind the same guard, or infirmary mode leaks
     * the link on small screens only.
     */
    @Test
    @DisplayName("mobile availability row should sit behind the same infirmary-view guard as the desktop link")
    void shouldGuardMobileEntryPoint_withInfirmaryViewCondition() throws IOException {
        String jsp = Files.readString(PROVIDER_DAY_JSP, StandardCharsets.UTF_8);

        int mobileRow = jsp.indexOf(MOBILE_ROW);
        assertThat(mobileRow).as("mobile availability row is present").isPositive();

        String before = jsp.substring(0, mobileRow);
        assertThat(before.lastIndexOf(INFIRMARY_GUARD))
                .as("nearest enclosing tag before the mobile row is an open infirmary guard")
                .isGreaterThan(before.lastIndexOf("</c:if>"));
    }

    /**
     * Both stylesheets are loaded mutually exclusively, so the desktop sheet hides the mobile
     * row and the mobile sheet styles it. Losing either rule silently drops one entry point.
     */
    @Test
    @DisplayName("availability entry point should be styled for both the desktop and mobile stylesheets")
    void shouldStyleAvailabilityEntryPoint_forBothStylesheets() throws IOException {
        assertThat(Files.readString(PROVIDER_DAY_CSS, StandardCharsets.UTF_8))
                .contains(".provider-availability-mobile-row {")
                .contains(".provider-availability-link:focus");
        assertThat(Files.readString(PROVIDER_DAY_MOBILE_CSS, StandardCharsets.UTF_8))
                .contains(".provider-availability-mobile-row .provider-availability-link {");
    }

    /**
     * Request parameters must be rejected before any template text reaches the writer,
     * otherwise a committed buffer swallows the status and the user gets a broken 200.
     */
    @Test
    @DisplayName("request parameters should be validated before any markup is emitted")
    void shouldRejectInvalidRequestParameters_beforeEmittingMarkup() throws IOException {
        String jsp = Files.readString(AVAILABILITY_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("SC_BAD_REQUEST, \"Invalid provider_no\"")
                .contains("SC_BAD_REQUEST, \"Invalid startDate\"")
                .contains("setLenient(false)")
                .contains("Locale.ROOT");

        int markupStart = jsp.indexOf("<!DOCTYPE html>");
        assertThat(markupStart).as("page emits a doctype").isPositive();
        assertThat(jsp.indexOf("SC_BAD_REQUEST, \"Invalid startDate\""))
                .as("startDate is rejected before output is buffered")
                .isLessThan(markupStart);
        assertThat(jsp.indexOf("SC_BAD_REQUEST, \"Invalid provider_no\""))
                .as("provider_no is rejected before output is buffered")
                .isLessThan(markupStart);
    }

    /**
     * The page previously fell back to a hardcoded provider "174". The replacement reads the
     * session preference, whose providerNo is null for providers with no saved row, so the
     * chain must stay null-safe and end in a 400 rather than a 500.
     */
    @Test
    @DisplayName("default provider resolution should be null-safe and never hardcoded")
    void shouldResolveProviderNullSafely_whenNoPreferenceRowExists() throws IOException {
        String jsp = Files.readString(AVAILABILITY_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .doesNotContain("174")
                .contains("providerPreference.getProviderNo()")
                .contains("getLoggedInProviderNo()")
                .contains("curProvider_no == null ||");

        assertThat(jsp.indexOf("getLoggedInProviderNo()"))
                .as("session fallback resolves before the validation guard dereferences it")
                .isLessThan(jsp.indexOf("curProvider_no == null ||"));
    }

    /**
     * Slots are the primary interaction. They must be real buttons (keyboard operable, not
     * {@code href="#"} anchors) inside a header-scoped table so the grid is navigable.
     */
    @Test
    @DisplayName("availability grid should use semantic headers and keyboard-operable slots")
    void shouldRenderAccessibleGrid_forKeyboardAndScreenReaders() throws IOException {
        String jsp = Files.readString(AVAILABILITY_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<button type=\"button\" class=\"availability-slot\"")
                .contains("<thead>")
                .contains("<tbody>")
                .contains("scope=\"col\"")
                .contains("scope=\"row\"")
                .contains("id=\"availabilityGrid\"")
                .contains("class=\"availability-grid-wrapper\"")
                .doesNotContain("<a class=\"availability-slot\" href=\"#\"");

        assertThat(jsp)
                .as("day-view links carry the provider so the target view stays in context")
                .contains("&amp;view=1&amp;curProvider=")
                .contains("&amp;curProviderName=");
    }

    /**
     * Configured colours reach a style attribute, so both paths must go through the shared
     * allowlist, and output must use the null-safe encoder wrappers rather than raw
     * {@code SafeEncode.forHtml}.
     */
    @Test
    @DisplayName("configured colours and untrusted values should use the shared validator and null-safe encoders")
    void shouldValidateColoursAndEncoding_forUntrustedValues() throws IOException {
        String jsp = Files.readString(AVAILABILITY_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("SAFE_CSS_COLOR_PATTERN");
        assertThat(jsp.split("getSafeCssColor\\(", -1).length - 1)
                .as("declaration plus both style-attribute call sites share one validator")
                .isGreaterThanOrEqualTo(3);
        assertThat(jsp)
                .as("forHtml is not null-safe; forHtmlContent/forHtmlAttribute are")
                .doesNotContain("SafeEncode.forHtml(");
    }

    /**
     * On screen the grid is a capped scroll container. Without a print override that cap
     * clips the schedule to roughly one viewport, which the previous plain table did not do.
     */
    @Test
    @DisplayName("availability stylesheet should scroll on screen and print the full grid")
    void shouldSupportStickyScrollAndFullPrintOutput_inAvailabilityCss() throws IOException {
        String css = Files.readString(AVAILABILITY_CSS, StandardCharsets.UTF_8);

        assertThat(css)
                .contains("position: sticky")
                .contains("overflow: auto")
                .contains(".availability-slot {");

        int print = css.indexOf("@media print");
        assertThat(print).as("stylesheet defines print behaviour").isPositive();
        assertThat(css.substring(print))
                .as("print releases the scroll cap and the viewport-relative sticky offsets")
                .contains("max-height: none")
                .contains("overflow: visible")
                .contains("position: static");
    }

    /**
     * The grid is always built from {@code curProvider_no}, so the selector must name that
     * provider on both branches. With no explicitly selected option the browser falls back to
     * the first entry, labelling this provider's schedule with a different, real provider —
     * and under multisite, where the group loop is skipped, the selector renders empty.
     */
    @Test
    @DisplayName("provider selector should mark a selected option whether or not the provider row resolves")
    void shouldAlwaysSelectRequestedProvider_evenWhenProviderRowIsMissing() throws IOException {
        String jsp = Files.readString(AVAILABILITY_JSP, StandardCharsets.UTF_8);

        int start = jsp.indexOf("<select id=\"availabilityProvider\"");
        assertThat(start).as("provider selector is present").isPositive();
        String selector = jsp.substring(start, jsp.indexOf("</select>", start));

        // Count the attribute marker rather than the bare word, which also occurs in prose.
        assertThat(selector.split(" selected>", -1).length - 1)
                .as("resolved and unresolved provider branches each emit a selected option")
                .isEqualTo(2);
        assertThat(selector)
                .as("the unresolved branch falls back to the requested provider")
                .contains("curProviderName");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir")));
    }
}
