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
 * Guards the discoverable schedule entry point and the provider availability
 * view's semantic, responsive layout.
 *
 * @since 2026-07-30
 */
@DisplayName("Provider availability view JSP regressions")
@Tag("unit")
@Tag("schedule")
class ScheduleAvailabilityViewJspRegressionTest {

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

    @Test
    @DisplayName("provider header should expose availability as a labelled link")
    void shouldExposeLabelledAvailabilityLink_inProviderHeader() throws IOException {
        String jsp = Files.readString(PROVIDER_DAY_JSP, StandardCharsets.UTF_8);
        String desktopCss = Files.readString(PROVIDER_DAY_CSS, StandardCharsets.UTF_8);
        String mobileCss = Files.readString(PROVIDER_DAY_MOBILE_CSS, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("class=\"provider-availability-link noprint\"")
                .contains("class=\"provider-availability-mobile-row noprint\"")
                .contains("provider.appointmentProviderAdminDay.viewAvailability")
                .contains("/schedule/FlipView?originalpage=")
                .contains("String.format(Locale.ROOT, \"%04d-%02d-%02d\", year, month, day)")
                .contains("class=\"fa-solid fa-calendar-days\"")
                .doesNotContain("<input type='radio' name='flipview'");

        int hiddenProviderHeader = jsp.indexOf("<td class=\"infirmaryView\"");
        int hiddenProviderHeaderClose = jsp.indexOf("</td>", hiddenProviderHeader);
        int mobileAvailabilityRow = jsp.indexOf("<tr class=\"provider-availability-mobile-row noprint\">");
        assertThat(mobileAvailabilityRow).isGreaterThan(hiddenProviderHeaderClose);

        assertThat(desktopCss)
                .contains(".provider-availability-link:focus,")
                .contains(".provider-availability-mobile-row {")
                .contains("display: none;");
        assertThat(mobileCss)
                .contains(".provider-availability-mobile-row .provider-availability-link {")
                .contains("display: inline-flex;");
    }

    @Test
    @DisplayName("availability view should use a responsive accessible grid")
    void shouldUseResponsiveAccessibleGrid_forAvailability() throws IOException {
        String jsp = Files.readString(AVAILABILITY_JSP, StandardCharsets.UTF_8);
        String css = Files.readString(AVAILABILITY_CSS, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<!DOCTYPE html>")
                .contains("<html lang=\"<%= SafeEncode.forHtmlAttribute(request.getLocale().toLanguageTag()) %>\">")
                .contains("/library/bootstrap/5.3.8/css/bootstrap.min.css")
                .contains("/css/scheduleavailability.css")
                .contains("private String getSafeCssColor(Object configuredColor)")
                .contains("new SimpleDateFormat(\"yyyy-MM-dd\", Locale.ROOT)")
                .contains("inform.setLenient(false)")
                .contains("String requestedStartDate = request.getParameter(\"startDate\")")
                .contains("providerPreference.getProviderNo()")
                .doesNotContain(": \"174\"")
                .contains("requestedStartDate.matches(\"[0-9]{4}-[0-9]{2}-[0-9]{2}\")")
                .contains("response.sendError(HttpServletResponse.SC_BAD_REQUEST, \"Invalid startDate\")")
                .contains("inform.format(lastMonth.getTime())")
                .contains("inform.format(nextMonth.getTime())")
                .contains("class=\"availability-grid-wrapper\"")
                .contains("id=\"availabilityGrid\"")
                .contains("<thead>")
                .contains("<tbody>")
                .contains("scope=\"col\"")
                .contains("scope=\"row\"")
                .contains("<nav class=\"btn-group\"")
                .contains("<button type=\"button\" class=\"availability-slot\"")
                .contains("aria-label=\"<%=SafeEncode.forHtmlAttribute(outform.format(cal.getTime()))%>")
                .contains("SafeEncode.forHtmlContent(outform.format(now.getTime()))")
                .contains("SafeEncode.forHtmlContent(temp.toString())")
                .doesNotContain("SafeEncode.forHtml(")
                .doesNotContain("role=\"group\"")
                .doesNotContain("<a class=\"availability-slot\" href=\"#\"")
                .contains("schedule.scheduleflipview.instructions");

        assertThat(jsp)
                .contains("day=<%=now.get(Calendar.DATE)%>&amp;view=1&amp;curProvider=<carlos:encode value='<%= curProvider_no %>' context=\"uriComponent\"/>")
                .contains("day=<%=cal.get(Calendar.DATE)%>&amp;view=1&amp;curProvider=<carlos:encode value='<%= curProvider_no %>' context=\"uriComponent\"/>")
                .contains("&amp;curProviderName=<carlos:encode value='<%= curProviderName %>' context=\"uriComponent\"/>");

        assertThat(jsp.indexOf("String requestedStartDate = request.getParameter(\"startDate\")"))
                .isLessThan(jsp.indexOf("<!DOCTYPE html>"));
        assertThat(jsp.indexOf("response.sendError(HttpServletResponse.SC_BAD_REQUEST, \"Invalid startDate\")"))
                .isLessThan(jsp.indexOf("<!DOCTYPE html>"));

        assertThat(css)
                .contains("#availabilityGrid thead th")
                .contains("#availabilityGrid .availability-date")
                .contains("overflow: auto")
                .contains(".availability-slot {")
                .contains("cursor: pointer")
                .contains("position: sticky");
    }

    @Test
    @DisplayName("provider selector should retain the current provider when its group is empty")
    void shouldRetainCurrentProvider_whenProviderGroupIsEmpty() throws IOException {
        String jsp = Files.readString(AVAILABILITY_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("Provider currentProvider = providerDao.getProvider(curProvider_no)")
                .contains("SAFE_CSS_COLOR_PATTERN.matcher(color).matches()")
                .contains("getSafeCssColor(ApptUtil.getColorFromLocation(sites, _loc))")
                .contains("currentProvider.getProviderNo()")
                .contains("currentProvider.getFormattedName()")
                .contains("if (mg.getId().getProviderNo().equals(curProvider_no))")
                .contains("continue;");
    }

    /**
     * The waiting-list booking popup opens this page without {@code provider_no}, and
     * {@code Login2Action} seeds a default-constructed {@code ProviderPreference} (null
     * {@code providerNo}) for providers with no saved preference row. Without the session
     * fallback the raw {@code matches(...)} guard dereferences null and the page 500s.
     */
    @Test
    @DisplayName("provider resolution should fall back to the session identity when no preference row exists")
    void shouldFallBackToSessionProvider_whenPreferenceProviderNoIsNull() throws IOException {
        String jsp = Files.readString(AVAILABILITY_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("import=\"io.github.carlos_emr.carlos.utility.LoggedInInfo\"")
                .contains("loggedInInfo != null ? loggedInInfo.getLoggedInProviderNo() : null")
                .contains("if (curProvider_no == null || !curProvider_no.matches(\"^[a-zA-Z0-9._-]+$\"))");

        // The session fallback must resolve before the validation guard, otherwise the guard
        // dereferences a null provider number instead of returning 400.
        assertThat(jsp.indexOf("loggedInInfo.getLoggedInProviderNo()"))
                .isLessThan(jsp.indexOf("if (curProvider_no == null || !curProvider_no.matches"));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir")));
    }
}
