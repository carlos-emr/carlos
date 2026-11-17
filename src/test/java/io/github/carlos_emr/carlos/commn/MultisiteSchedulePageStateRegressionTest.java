/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The multisite schedule pages keep their clinic state per request and show a clinic only while the login is
 * offered it.
 * <p>
 * A JSP declaration ({@code <%! %>}) makes a field of the page's single servlet instance, which every request of
 * every login shares. The pages' site lists, chosen clinic and colour maps must therefore be locals.
 *
 * @since 2026-10-18
 */
@DisplayName("Multisite schedule pages - per-request clinic state")
@Tag("unit")
@Tag("scheduling")
class MultisiteSchedulePageStateRegressionTest {

    private static final String JSP_ROOT = "src/main/webapp/WEB-INF/jsp/";
    private static final String DAY_SHEET = "provider/appointmentprovideradminday.jsp";
    private static final String MONTH_VIEW = "provider/appointmentprovideradminmonth.jsp";
    private static final String PREFERENCE = "provider/providerupdatepreference.jsp";
    private static final Pattern DECLARATION = Pattern.compile("<%!(.*?)%>", Pattern.DOTALL);
    /** A field declaration (not a method parameter) of any of the pages' multisite names. */
    private static final Pattern SITE_FIELD = Pattern.compile(
            "[\\w<>\\[\\],.\\s]+?\\b(bMultisites|sites|curUserSites|siteProviderNos|siteGroups|selectedSite|siteBgColor"
                    + "|CurrentSiteMap|curScheduleMultisite|jdbc)\\s*(=|;)");

    private static String read(String jsp) throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth <= 8 && current != null; depth++) {
            Path candidate = current.resolve(JSP_ROOT + jsp);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + JSP_ROOT + jsp);
    }

    /** The field declarations in the page's declaration blocks; method bodies are cut out first. */
    private static List<String> siteFields(String page) {
        List<String> fields = new ArrayList<>();
        Matcher block = DECLARATION.matcher(page);
        while (block.find()) {
            String members = withoutMethodBodies(block.group(1));
            Matcher field = SITE_FIELD.matcher(members);
            while (field.find()) {
                fields.add(field.group().trim());
            }
        }
        return fields;
    }

    private static String withoutMethodBodies(String members) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (char c : members.toCharArray()) {
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (depth == 0) {
                out.append(c);
            }
        }
        return out.toString();
    }

    @ParameterizedTest
    @ValueSource(strings = {DAY_SHEET, MONTH_VIEW, "demographic/demographicappthistory.jsp", "schedule/scheduleflipview.jsp"})
    @DisplayName("should declare no clinic state as a field of the shared servlet")
    void shouldDeclareNoSiteField_inDeclarationBlocks(String jsp) throws IOException {
        assertThat(siteFields(read(jsp))).as(jsp).isEmpty();
    }

    @Test
    @DisplayName("should find a field declared in a declaration block, so the scan itself works")
    void shouldFindSiteField_whenDeclaredAsField() {
        String page = "<%!\n    private HashMap<String, String> CurrentSiteMap = new HashMap<>();\n"
                + "    private String html(List<Site> sites) { String selectedSite = null; return \"\"; }\n%>";
        assertThat(siteFields(page)).containsExactly("private HashMap<String, String> CurrentSiteMap =");
    }

    @Test
    @DisplayName("should read the remembered clinic only through the login's offered clinics")
    void shouldReadRememberedSite_throughOfferedSites() throws IOException {
        String day = read(DAY_SHEET);
        assertThat(day)
                .contains("SiteChoice.offeredOrNull(\n                (String) session.getAttribute(\"site_selected\"), curUserSites)")
                .doesNotContain("selectedSite = (String) session.getAttribute(\"site_selected\")");
        String month = read(MONTH_VIEW);
        assertThat(month)
                .contains("requestSite = io.github.carlos_emr.carlos.commn.SiteChoice.offeredOrNull(requestSite, curUserSites);")
                .contains("requestSite == null ? (String) session.getAttribute(\"site_selected\") : requestSite, curUserSites);");
        // The offered clinics must be known before the choice is checked against them.
        assertThat(day.indexOf("curUserSites = siteDao.getActiveSitesByProviderNo"))
                .isLessThan(day.indexOf("SiteChoice.offeredOrNull("));
        assertThat(month.indexOf("curUserSites = siteDao.getActiveSitesByProviderNo"))
                .isLessThan(month.indexOf("SiteChoice.offeredOrNull("));
    }

    @Test
    @DisplayName("should remember only a clinic the login's selector offers")
    void shouldRememberOfferedSiteOnly_onPreferenceSave() throws IOException {
        String preference = read(PREFERENCE);
        assertThat(preference)
                .contains("SiteChoice.offeredOrNull(selected_site,")
                .contains("SiteChoice.hasAccessPrivacy(securityInfoManager, loggedInInfo)")
                .contains("session.setAttribute(\"site_selected\", offeredSite);")
                .doesNotContain("session.setAttribute(\"site_selected\", (selected_site.equals(\"none\") ? null : selected_site));");
    }

    @Test
    @DisplayName("should encode the day sheet's clinic names and colours")
    void shouldEncodeSiteOutputs_onDaySheet() throws IOException {
        String day = read(DAY_SHEET);
        assertThat(day)
                .doesNotContain("<%=curUserSites.get(i).getName()%>")
                .doesNotContain("<%=curUserSites.get(i).getBgColor()%>")
                .doesNotContain("<span title=\"<%= sitename %>\"")
                .doesNotContain("<%=siteBgColor.get(sitename)%>")
                .contains("<option value=\"<carlos:encode value='<%= curUserSites.get(i).getName() %>' context=\"htmlAttribute\"/>\"")
                .contains("<carlos:encode value='<%= curUserSites.get(i).getName() %>'/>")
                .contains("<span title=\"<carlos:encode value='<%= sitename %>' context=\"htmlAttribute\"/>\"")
                .contains("SiteChoice.cssColor(siteBgColor.get(sitename), \"\")");
    }

    @Test
    @DisplayName("should encode the month view's clinic labels and colours")
    void shouldEncodeSiteOutputs_onMonthView() throws IOException {
        String month = read(MONTH_VIEW);
        assertThat(month)
                .contains("SafeEncode.forHtmlContent(ApptUtil.getShortNameFromLocation(sites, reason))")
                .contains("SiteChoice.cssColor(ApptUtil.getColorFromLocation(sites, reason), \"\")")
                .contains("<span class='datepreason'><carlos:encode value='<%= date.getReason() %>'/></span>")
                .contains("\"&site=\" + encodeURIComponent(siteName)")
                .doesNotContain("\"&site=\" + siteName");
    }
}
