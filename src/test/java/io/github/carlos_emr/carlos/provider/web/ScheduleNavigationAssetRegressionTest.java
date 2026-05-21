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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards schedule navigation JSP defaults that are otherwise only exercised
 * through provider schedule rendering.
 *
 * @since 2026-05-20
 */
@DisplayName("Schedule navigation asset regressions")
@Tag("unit")
@Tag("provider")
class ScheduleNavigationAssetRegressionTest {

    private static final Path SCHEDULE_PAGE_SCRIPT =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "schedulePage.js.jsp");
    private static final Path PROVIDER_PREFERENCE_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "providerpreference.jsp");
    private static final Path DOCUMENT_REPORT_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "documentManager", "documentReport.jsp");
    private static final Path DISPLAY_MESSAGES_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "messenger", "DisplayMessages.jsp");
    private static final Path VIEW_MESSAGE_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "messenger", "ViewMessage.jsp");
    private static final Path CREATE_MESSAGE_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "messenger", "CreateMessage.jsp");
    private static final Path MAIN_MENU_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "mainMenu.jsp");
    private static final Path TOPNAV_CSS =
            Path.of("src", "main", "webapp", "css", "topnav.css");
    /**
     * Verifies sortable mailbox-header URLs keep their existing query state and
     * append the schedule navigation flag after box/demographic parameters.
     */
    private static final String STATUS_SORT_LINK_PATTERN =
            "DisplayMessages?orderby=status<%=boxTypeQuerySuffix%>"
                    + "<%=demographicQuerySuffix%><%=scheduleNavQuerySuffix%>";
    /**
     * Verifies message-detail links retain both the selected mailbox and the
     * focused schedule-shell flag when users drill into an individual message.
     */
    private static final String MESSAGE_LINK_PATTERN =
            "ViewMessage?messageID=<carlos:encode value='<%= dm.getMessageId() %>'"
                    + " context=\"uriComponent\"/>&boxType=<%=pageType%><%=scheduleNavQuerySuffix%>";

    @Test
    @DisplayName("should resolve schedule navigation without Carlosdoc provider special case")
    void shouldResolveScheduleNavigation_withoutCarlosdocProviderSpecialCase() throws IOException {
        String scheduleScript = Files.readString(SCHEDULE_PAGE_SCRIPT, StandardCharsets.UTF_8);
        String providerPreference = Files.readString(PROVIDER_PREFERENCE_JSP, StandardCharsets.UTF_8);
        String normalizedScheduleScript = normalizeWhitespace(scheduleScript);
        String normalizedProviderPreference = normalizeWhitespace(providerPreference);

        assertThat(normalizedScheduleScript)
                .contains("String scheduleNavigationMode = UserProperty.SCHEDULE_NAVIGATION_MODE_POPUP;")
                .contains("scheduleNavigationMode = UserProperty.resolveScheduleNavigationMode( savedMode,"
                        + " tabProp != null && \"yes\".equalsIgnoreCase(tabProp.getValue()));")
                .doesNotContain("CARLOSDOC_PROVIDER_NO");
        assertThat(normalizedProviderPreference)
                .contains("String scheduleNavigationMode = UserProperty.resolveScheduleNavigationMode("
                        + " props.get(UserProperty.SCHEDULE_NAVIGATION_MODE), encOpenInTab);")
                .doesNotContain("CARLOSDOC_PROVIDER_NO");
    }


    @Test
    @DisplayName("should keep schedule navigation styled and propagated on destination pages")
    void shouldPreserveScheduleNavigation_onDestinationPages() throws IOException {
        String documentReport = Files.readString(DOCUMENT_REPORT_JSP, StandardCharsets.UTF_8);
        String displayMessages = Files.readString(DISPLAY_MESSAGES_JSP, StandardCharsets.UTF_8);
        String viewMessage = Files.readString(VIEW_MESSAGE_JSP, StandardCharsets.UTF_8);
        String createMessage = Files.readString(CREATE_MESSAGE_JSP, StandardCharsets.UTF_8);
        String mainMenu = Files.readString(MAIN_MENU_JSP, StandardCharsets.UTF_8);
        String scheduleScript = Files.readString(SCHEDULE_PAGE_SCRIPT, StandardCharsets.UTF_8);
        String topnavCss = Files.readString(TOPNAV_CSS, StandardCharsets.UTF_8);

        assertThat(documentReport)
                .contains("<link rel=\"stylesheet\" href=\"<%=request.getContextPath()%>/css/topnav.css\">")
                .contains("<jsp:include page=\"/WEB-INF/jsp/provider/mainMenu.jsp\"/>");
        assertThat(topnavCss)
                .contains("table#firstTable .dashboardDropdown")
                .contains("table#firstTable .dropdown:hover .dashboardDropdown");
        assertThat(displayMessages)
                .contains("String boxTypeQuerySuffix = pageType > 0 ? \"&boxType=\" + pageType : \"\";")
                .contains("String demographicQuerySuffix = pageType == 3 && demographic_no != null")
                .contains("ViewCreateMessage<%=scheduleNavFirstQuerySuffix%>")
                .contains(STATUS_SORT_LINK_PATTERN)
                .contains(MESSAGE_LINK_PATTERN);
        assertThat(viewMessage)
                .contains("boolean showScheduleNav = \"1\".equals(request.getParameter(\"scheduleNav\"));")
                .contains("<jsp:include page=\"/WEB-INF/jsp/provider/mainMenu.jsp\"/>")
                .contains("DisplayMessages<%=scheduleNavFirstQuerySuffix%>")
                .contains("DisplayMessages?boxType=1<%=scheduleNavQuerySuffix%>");
        assertThat(createMessage)
                .contains("boolean showScheduleNav = \"1\".equals(request.getParameter(\"scheduleNav\"));")
                .contains("<jsp:include page=\"/WEB-INF/jsp/provider/mainMenu.jsp\"/>")
                .contains("ClearMessage<%=scheduleNavFirstQuerySuffix%>")
                .contains("DisplayMessages<%=scheduleNavFirstQuerySuffix%>");
        assertThat(mainMenu)
                .contains("!window.popup.scheduleMenuFallback")
                .contains("fallbackMenuPopup.scheduleMenuFallback = true;")
                .contains("window.popup = fallbackMenuPopup;");
        assertThat(scheduleScript)
                .contains("var usesScheduleShell = scheduleNavigationMode === 'focused'"
                        + " || scheduleNavigationMode === 'tab';")
                .contains("var targetUrl = usesScheduleShell ? appendQueryParam(url, 'scheduleNav', '1')"
                        + " : url;")
                .contains("popupAction(targetUrl);");
    }

    /**
     * Collapses JSP whitespace sequences to single spaces so assertions focus
     * on defaulting behavior instead of indentation or line wrapping.
     */
    private static String normalizeWhitespace(String content) {
        return content.replaceAll("\\s+", " ").trim();
    }
}
