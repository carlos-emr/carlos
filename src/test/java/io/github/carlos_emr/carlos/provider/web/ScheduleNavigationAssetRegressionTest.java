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
    private static final Path PROVIDER_UPDATE_PREFERENCE_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "providerupdatepreference.jsp");
    private static final Path DOCUMENT_REPORT_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "documentManager", "documentReport.jsp");
    private static final Path DISPLAY_MESSAGES_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "messenger", "DisplayMessages.jsp");
    private static final Path VIEW_MESSAGE_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "messenger", "ViewMessage.jsp");
    private static final Path CREATE_MESSAGE_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "messenger", "CreateMessage.jsp");
    private static final Path SENT_MESSAGE_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "messenger", "SentMessage.jsp");
    private static final Path MESSENGER_SCHEDULE_NAV_JSPF =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "messenger", "messengerScheduleNav.jspf");
    private static final Path INBOXHUB_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "web", "inboxhub", "Inboxhub.jsp");
    private static final Path TICKLER_MAIN_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "tickler", "ticklerMain.jsp");
    private static final Path MAIN_MENU_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "mainMenu.jsp");
    private static final Path REPORT_INDEX_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "report", "reportindex.jsp");
    private static final Path APPOINTMENT_PROVIDER_DAY_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "appointmentprovideradminday.jsp");
    private static final Path TOPNAV_CSS =
            Path.of("src", "main", "webapp", "css", "topnav.css");
    private static final Path RECEPTIONIST_APPT_CSS =
            Path.of("src", "main", "webapp", "css", "receptionistapptstyle.css");
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
    @DisplayName("should broadcast saved schedule navigation mode with shared resolver")
    void shouldBroadcastScheduleNavigationMode_whenPreferenceSaved() throws IOException {
        String providerUpdatePreference = Files.readString(PROVIDER_UPDATE_PREFERENCE_JSP, StandardCharsets.UTF_8);
        String normalizedProviderUpdatePreference = normalizeWhitespace(providerUpdatePreference);

        assertThat(normalizedProviderUpdatePreference)
                .contains("savedScheduleNavigationMode = UserProperty.resolveScheduleNavigationMode("
                        + " submittedScheduleNavigationMode, false);")
                .contains("mode: '<%= SafeEncode.forJavaScript(savedScheduleNavigationMode) %>'")
                .contains("self.opener.applyScheduleNavigationPreference(scheduleNavigationPreferencePayload.mode);")
                .contains("new BroadcastChannel('carlos_schedule_navigation_mode')")
                .contains("localStorage.setItem('carlos_schedule_navigation_mode',"
                        + " JSON.stringify(scheduleNavigationPreferencePayload));")
                .doesNotContain("!UserProperty.SCHEDULE_NAVIGATION_MODE_TAB.equals(savedScheduleNavigationMode)");
    }

    @Test
    @DisplayName("should compose menu preference opener hook")
    void shouldComposePreferenceHook_whenMenuIncluded() throws IOException {
        String mainMenu = Files.readString(MAIN_MENU_JSP, StandardCharsets.UTF_8);
        String normalizedMainMenu = normalizeWhitespace(mainMenu);

        assertThat(normalizedMainMenu)
                .contains("var existingApplyScheduleNavigationPreference ="
                        + " window.applyScheduleNavigationPreference;")
                .contains("window.applyScheduleNavigationPreference = function(mode) {"
                        + " applyScheduleMenuNavigationPreference(mode);"
                        + " if (typeof existingApplyScheduleNavigationPreference === 'function'"
                        + " && existingApplyScheduleNavigationPreference !=="
                        + " applyScheduleMenuNavigationPreference) {"
                        + " existingApplyScheduleNavigationPreference(mode); } };")
                .doesNotContain("if (typeof window.applyScheduleNavigationPreference !== 'function') {"
                        + " window.applyScheduleNavigationPreference = applyScheduleMenuNavigationPreference; }");
    }


    @Test
    @DisplayName("should keep schedule navigation styled and propagated on destination pages")
    void shouldPreserveScheduleNavigation_onDestinationPages() throws IOException {
        String documentReport = Files.readString(DOCUMENT_REPORT_JSP, StandardCharsets.UTF_8);
        String displayMessages = Files.readString(DISPLAY_MESSAGES_JSP, StandardCharsets.UTF_8);
        String viewMessage = Files.readString(VIEW_MESSAGE_JSP, StandardCharsets.UTF_8);
        String createMessage = Files.readString(CREATE_MESSAGE_JSP, StandardCharsets.UTF_8);
        String sentMessage = Files.readString(SENT_MESSAGE_JSP, StandardCharsets.UTF_8);
        String messengerScheduleNav = Files.readString(MESSENGER_SCHEDULE_NAV_JSPF, StandardCharsets.UTF_8);
        String normalizedMessengerScheduleNav = normalizeWhitespace(messengerScheduleNav);
        String normalizedSentMessage = normalizeWhitespace(sentMessage);
        String inboxhub = Files.readString(INBOXHUB_JSP, StandardCharsets.UTF_8);
        String ticklerMain = Files.readString(TICKLER_MAIN_JSP, StandardCharsets.UTF_8);
        String normalizedTicklerMain = normalizeWhitespace(ticklerMain);
        String mainMenu = Files.readString(MAIN_MENU_JSP, StandardCharsets.UTF_8);
        String reportIndex = Files.readString(REPORT_INDEX_JSP, StandardCharsets.UTF_8);
        String appointmentProviderDay = Files.readString(APPOINTMENT_PROVIDER_DAY_JSP, StandardCharsets.UTF_8);
        String scheduleScript = Files.readString(SCHEDULE_PAGE_SCRIPT, StandardCharsets.UTF_8);
        String topnavCss = Files.readString(TOPNAV_CSS, StandardCharsets.UTF_8);
        String receptionistApptCss = Files.readString(RECEPTIONIST_APPT_CSS, StandardCharsets.UTF_8);

        assertThat(documentReport)
                .contains("<link rel=\"stylesheet\" href=\"<%=request.getContextPath()%>/css/topnav.css\">")
                .contains("<jsp:include page=\"/WEB-INF/jsp/provider/mainMenu.jsp\"/>");
        assertThat(topnavCss)
                .contains("table#firstTable .dashboardDropdown")
                .contains("table#firstTable .dropdown:hover .dashboardDropdown")
                .contains("ul#navlist > li:not(.dashboardDropdown)")
                .contains("ul#navlist > li:not(.dashboardDropdown) > a")
                .doesNotContain("ul#navlist li:not(.dashboardDropdown)")
                .doesNotContain("ul#navlist li a:not(.dashboardDropdown)")
                .contains("li.nav-active > a")
                .contains("li.nav-active:hover")
                .contains("li.nav-active > a:hover")
                .contains("li.nav-active > a:focus")
                .contains("li.nav-active > a.tabalert")
                .contains("li.nav-active > a span");
        assertThat(receptionistApptCss)
                .contains("li.nav-active > a")
                .contains("li.nav-active:hover")
                .contains("li.nav-active > a:hover")
                .contains("li.nav-active > a:focus")
                .contains("li.nav-active > a.tabalert")
                .contains("li.nav-active > a span");
        assertThat(displayMessages)
                .contains("<%@ include file=\"messengerScheduleNav.jspf\" %>")
                .contains("String boxTypeQuerySuffix = pageType > 0 ? \"&boxType=\" + pageType : \"\";")
                .contains("String demographicQuerySuffix = pageType == 3 && demographic_no != null")
                .contains("ViewCreateMessage<%=scheduleNavFirstQuerySuffix%>")
                .contains(STATUS_SORT_LINK_PATTERN)
                .contains(MESSAGE_LINK_PATTERN);
        assertThat(messengerScheduleNav)
                .contains("boolean showScheduleNav = \"1\".equals(request.getParameter(\"scheduleNav\"));")
                .contains("String scheduleNavQuerySuffix = showScheduleNav ? \"&scheduleNav=1\" : \"\";")
                .contains("String scheduleNavFirstQuerySuffix = showScheduleNav ? \"?scheduleNav=1\" : \"\";");
        assertThat(normalizedMessengerScheduleNav)
                .contains("boolean showMessengerExitButton = !showScheduleNav "
                        + "|| !UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED.equals(messengerScheduleNavigationMode);")
                .doesNotContain("UserProperty.SCHEDULE_NAVIGATION_MODE_TAB.equals(messengerScheduleNavigationMode)");
        assertThat(viewMessage)
                .contains("<%@ include file=\"messengerScheduleNav.jspf\" %>")
                .contains("<jsp:include page=\"/WEB-INF/jsp/provider/mainMenu.jsp\"/>")
                .contains("DisplayMessages<%=scheduleNavFirstQuerySuffix%>")
                .contains("DisplayMessages?boxType=1<%=scheduleNavQuerySuffix%>")
                .contains("ViewCreateMessage<%=scheduleNavFirstQuerySuffix%>");
        assertThat(createMessage)
                .contains("<%@ include file=\"messengerScheduleNav.jspf\" %>")
                .contains("<jsp:include page=\"/WEB-INF/jsp/provider/mainMenu.jsp\"/>")
                .contains("<input type=\"hidden\" name=\"scheduleNav\" value=\"1\">")
                .contains("ClearMessage<%=scheduleNavFirstQuerySuffix%>")
                .contains("DisplayMessages<%=scheduleNavFirstQuerySuffix%>");
        assertThat(sentMessage)
                .contains("<%@ include file=\"messengerScheduleNav.jspf\" %>")
                .contains("ViewCreateMessage<%=scheduleNavFirstQuerySuffix%>")
                .contains("DisplayMessages<%=scheduleNavFirstQuerySuffix%>");
        assertThat(normalizedSentMessage)
                .contains("<% if (showScheduleNav) { %> <link rel=\"stylesheet\" href=\"<%=request.getContextPath()%>/css/topnav.css\"> <% } %>")
                .contains("<% if (showScheduleNav) { %> <jsp:include page=\"/WEB-INF/jsp/provider/mainMenu.jsp\"/> <% } %>");
        assertThat(inboxhub)
                .contains("<c:if test=\"${param.scheduleNav eq '1'}\">")
                .contains("<link rel=\"stylesheet\" type=\"text/css\" href=\"${pageContext.request.contextPath}/css/topnav.css\"/>")
                .contains("<jsp:include page=\"/WEB-INF/jsp/provider/mainMenu.jsp\"/>")
                .contains("const inboxContextPath =")
                .doesNotContain("const contextPath =");
        assertThat(ticklerMain)
                .contains("boolean showScheduleNav = \"1\".equals(request.getParameter(\"scheduleNav\"));")
                .contains("action=\"<%= request.getContextPath() %>/tickler/ViewTicklerMain\"")
                .contains("<input type=\"hidden\" name=\"scheduleNav\" value=\"1\">");
        assertThat(normalizedTicklerMain)
                .contains("<% if (showScheduleNav) { %> <link rel=\"stylesheet\" href=\"<%=request.getContextPath()%>/css/topnav.css\"> <% } %>")
                .contains("<% if (showScheduleNav) { %> <jsp:include page=\"/WEB-INF/jsp/provider/mainMenu.jsp\"/> <% } %>");
        assertThat(mainMenu)
                .contains("<oscar:newLab providerNo=\"<%=curUser_no%>\">")
                .contains("</oscar:newLab>")
                .contains("<oscar:newTickler providerNo=\"<%=curUser_no%>\">")
                .contains("</oscar:newTickler>")
                .contains("<oscar:newMessage providerNo=\"<%=curUser_no%>\">")
                .contains("</oscar:newMessage>")
                .contains("NavPath.requestPathMatches")
                .doesNotContain("private boolean requestPathMatches")
                .doesNotContain("private boolean pathMatches")
                .contains("<td id=\"firstMenu\">")
                .contains("<div class=\"icon-container\">")
                .contains("<td id=\"userSettings\">")
                .contains("<a id=\"logoutButton\"")
                .contains("scheduleTabActive")
                .contains("messengerTabActive")
                .contains("requestPathMatches(request, \"/provider/providercontrol\",")
                .contains("\"/provider/appointmentprovideradmin\", \"/provider/appointmentprovideradminday\")")
                .contains("class=\"<%= scheduleTabActive ? \"nav-active\" : \"\" %>\"")
                .contains("class=\"<%= inboxTabActive ? \"nav-active\" : \"\" %>\"")
                .contains("HREF=\"<%= scheduleNavActive ? request.getContextPath() + \"/web/inboxhub/Inboxhub?method=displayInboxForm&scheduleNav=1\" : \"#\" %>\" id=\"inboxLink\"")
                .contains("HREF=\"<%= scheduleNavActive ? request.getContextPath() + \"/web/inboxhub/Inboxhub?method=displayInboxForm&unclaimed=1&scheduleNav=1\" : \"javascript:void(0)\" %>\"")
                .contains("class=\"<%= messengerTabActive ? \"nav-active\" : \"\" %>\"")
                .contains("var inboxUrl = contextPath + \"/web/inboxhub/Inboxhub?method=displayInboxForm\";")
                .contains("return openScheduleMenuSection('\" + inboxUrl + \"', function(u){ popupInboxManager(u, 800); }, event);")
                .contains("!window.popup.scheduleMenuFallback")
                .contains("fallbackMenuPopup.scheduleMenuFallback = true;")
                .contains("window.popup = fallbackMenuPopup;")
                .doesNotContain("navRequestPath")
                .doesNotContain("(String) request.getAttribute")
                .doesNotContain("resourceTabActive")
                .doesNotContain("encounter.Index.clinicalResources");
        assertThat(appointmentProviderDay)
                .contains("scheduleNavActiveClass = NavPath.requestPathMatches(request,")
                .contains("/provider/appointmentprovideradminday")
                .contains("<li class=\"<%= scheduleNavActiveClass %>\">")
                .contains("<td class=\"icon-container\">")
                .contains("<oscar:newLab providerNo=\"<%=loggedInInfo1.getLoggedInProviderNo()%>\">")
                .contains("</oscar:newLab>")
                .contains("<oscar:newTickler providerNo=\"<%=loggedInInfo1.getLoggedInProviderNo()%>\">")
                .contains("</oscar:newTickler>")
                .contains("<oscar:newMessage providerNo=\"<%=loggedInInfo1.getLoggedInProviderNo()%>\">")
                .contains("</oscar:newMessage>")
                .contains("id=\"helpLink\"")
                .contains("${carlos:forJavaScriptAttribute(scheduleResourceBaseUrl)}")
                .contains("HREF=\"<%= \"1\".equals(request.getParameter(\"scheduleNav\")) ? request.getContextPath() + \"/web/inboxhub/Inboxhub?method=displayInboxForm&scheduleNav=1\" : \"#\" %>\" id=\"inboxLink\"")
                .contains("HREF=\"<%= \"1\".equals(request.getParameter(\"scheduleNav\")) ? request.getContextPath() + \"/web/inboxhub/Inboxhub?method=displayInboxForm&unclaimed=1&scheduleNav=1\" : \"javascript:void(0)\" %>\"")
                .contains("const inboxUrl = contextPath + \"/web/inboxhub/Inboxhub?method=displayInboxForm\";")
                .contains("return openScheduleSection('\" + inboxUrl + \"', function(u){ popupInboxManager(u, 800); }, event);")
                .doesNotContain("openScheduleMenuSection")
                .doesNotContain("popupInboxManager('\" + contextPath + \"/web/inboxhub/Inboxhub?method=displayInboxForm', 800);return false;")
                .doesNotContain("encounter.Index.clinicalResources");
        assertThat(documentReport)
                .contains("<div class=\"container-fluid carlos-content-shell\" style=\"margin-bottom: 25px\">")
                .doesNotContain("<div class=\"container\" style=\"margin-bottom: 25px\">");
        assertThat(ticklerMain)
                .contains("<div class=\"container-fluid carlos-content-shell\">")
                .doesNotContain("<div class=\"container\">");
        assertThat(reportIndex)
                .contains("<div class=\"container-fluid carlos-content-shell\">")
                .doesNotContain("<div class=\"container\">");
        assertThat(topnavCss)
                .contains(".carlos-content-shell")
                .contains("padding-left: 0;")
                .contains("padding-right: 0;");
        assertThat(scheduleScript)
                .contains("var usesScheduleShell = scheduleNavigationMode === 'focused'"
                        + " || scheduleNavigationMode === 'tab';")
                .contains("var targetUrl = usesScheduleShell ? appendQueryParam(url, 'scheduleNav', '1')"
                        + " : url;")
                .contains("popupAction(targetUrl);");
    }

    @Test
    @DisplayName("should expose appointment hover details on schedule entries")
    void shouldExposeAppointmentHoverDetails_onScheduleEntries() throws IOException {
        String appointmentProviderDay = Files.readString(APPOINTMENT_PROVIDER_DAY_JSP, StandardCharsets.UTF_8);
        String scheduleScript = Files.readString(SCHEDULE_PAGE_SCRIPT, StandardCharsets.UTF_8);

        assertThat(appointmentProviderDay)
                .contains("appendTooltipLine(appointmentTooltipSummaryBuilder, \"Reason\", reasonCodeName);")
                .contains("appendTooltipLine(appointmentTooltipFullBuilder, \"Appointment notes\", notes);")
                .contains("appendTooltipLine(appointmentTooltipFullBuilder, \"Ticklers\", tickler_note);")
                .contains("appendTooltipLine(appointmentTooltipFullBuilder, \"Demographic alerts\", demographicAlert);")
                .contains("appendTooltipLine(appointmentTooltipFullBuilder, \"Demographic notes\", demographicNotes);")
                .contains("appendTooltipLine(appointmentTooltipFullBuilder, \"Prevention alerts\", preventionWarning);")
                .contains("class=\"appt<%= isCancelled ? \" Cancelled\" : \"\" %><%= showTooltip ?"
                        + " \" appt-reason-tooltip appt-tooltip-provider-\" + curProvider_no[nProvider] : \"\" %>\"")
                .contains("data-title-full=\\\"\" + appointmentTooltipFull + \"\\\"")
                .contains("data-title-short=\\\"\" + appointmentTooltipSummary + \"\\\"");
        assertThat(scheduleScript)
                .contains("updateTooltipsForProvider(providerNo, showReason);")
                .contains("const titleAttr = showReason ? el.dataset.titleFull : el.dataset.titleShort;");
    }

    /**
     * Collapses JSP whitespace sequences to single spaces so assertions focus
     * on defaulting behavior instead of indentation or line wrapping.
     */
    private static String normalizeWhitespace(String content) {
        return content.replaceAll("\\s+", " ").trim();
    }
}
