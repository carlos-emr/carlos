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
package io.github.carlos_emr.carlos.commn.web;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.commn.model.Site;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Satellite-sites Admin pages, as pages (issue #4700): the detail form's fields bind to the action's
 * site and are filled from it, and the list opens a site through a link the GET guard lets through. The
 * browser check {@code admin-manage-sites} drives the same pages live (with {@code multisites=on}).
 */
@Tag("unit")
@Tag("web")
@DisplayName("Satellite-sites Admin JSP regressions")
class SitesAdminJspRegressionTest {

    private static final Path LIST_JSP = Path.of("src/main/webapp/WEB-INF/jsp/admin/sitesAdmin.jsp");
    private static final Path DETAIL_JSP = Path.of("src/main/webapp/WEB-INF/jsp/admin/sitesAdminDetail.jsp");
    private static final Pattern SITE_FIELD = Pattern.compile("name=\"site\\.(\\w+)\"");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static List<String> siteFields(String page) {
        Matcher matcher = SITE_FIELD.matcher(page);
        return matcher.results().map(result -> result.group(1)).toList();
    }

    /** The form posts site.*, so the action must expose its site as "site", open one level deep. */
    @Test
    @DisplayName("should bind every site.* field of the form to the action's site")
    void shouldBindFormFields_toActionSite() throws Exception {
        List<String> fields = siteFields(read(DETAIL_JSP));
        assertThat(fields).contains("name", "shortName", "bgColor", "status", "phone", "fax", "address", "city",
                "province", "postal", "providerIdFrom", "providerIdTo", "siteId");

        Method getter = SitesManage2Action.class.getMethod("getSite");
        assertThat(getter.getReturnType()).isEqualTo(Site.class);
        StrutsParameter parameter = getter.getAnnotation(StrutsParameter.class);
        assertThat(parameter).as("getSite() must allow site.* parameters").isNotNull();
        assertThat(parameter.depth()).isGreaterThanOrEqualTo(1);
        for (String field : fields) {
            String setter = "set" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
            assertThat(java.util.Arrays.stream(Site.class.getMethods()).anyMatch(m -> m.getName().equals(setter)))
                    .as("Site has no %s for the form's site.%s", setter, field).isTrue();
        }
    }

    /** An opened site shows its stored values, and its hidden id makes Save update it instead of adding one. */
    @Test
    @DisplayName("should fill each text field and the hidden id from the site, encoded for the attribute")
    void shouldFillFields_fromSite() throws IOException {
        String page = read(DETAIL_JSP);

        for (String field : List.of("name", "shortName", "bgColor", "phone", "fax", "address", "city", "postal",
                "providerIdFrom", "providerIdTo", "siteId")) {
            assertThat(page).containsPattern(Pattern.compile(
                    "name=\"site\\." + field + "\"[^>]*value=\"\\$\\{carlos:forHtmlAttribute\\(site\\." + field + "\\)\\}\""));
        }
        assertThat(page).contains("name=\"site.status\" value=\"1\" <c:if test=\"${site.status == 1}\">checked</c:if>");
        assertThat(page).contains("<c:if test=\"${site.province == code}\">selected</c:if>");
    }

    /** Each visible field of the form has an id, and its caption is a {@code <label for>} that id. */
    @Test
    @DisplayName("should label every visible site field with its caption")
    void shouldLabelEveryVisibleField_withCaption() throws IOException {
        String page = read(DETAIL_JSP);
        Matcher field = Pattern.compile("<(input|select)\\b(?![^>]*type=\"hidden\")[^>]*name=\"site\\.(\\w+)\"[^>]*>").matcher(page);
        int labelled = 0;
        while (field.find()) {
            Matcher id = Pattern.compile("\\bid=\"([^\"]+)\"").matcher(field.group());
            assertThat(id.find()).as("site.%s has no id", field.group(2)).isTrue();
            assertThat(page).as("site.%s has no label", field.group(2)).contains("<label for=\"" + id.group(1) + "\">");
            labelled++;
        }
        assertThat(labelled).isEqualTo(12);
    }

    /** A save missing a required field comes back to the form naming each one. */
    @Test
    @DisplayName("should name each missing required field on the form")
    void shouldNameMissingFields_onForm() throws IOException {
        assertThat(read(DETAIL_JSP)).containsSubsequence(
                "<c:forEach items=\"${missingFields}\" var=\"labelKey\">",
                "<fmt:message key=\"errors.required\"><fmt:param><fmt:message key=\"${labelKey}\"/></fmt:param></fmt:message>");
    }

    /** HttpMethodGuardFilter refuses GET with method=update, so the list's GET link opens with method=edit. */
    @Test
    @DisplayName("should open a site from the list with the read-only method=edit")
    void shouldOpenSite_withEditMethod() throws IOException {
        String page = read(LIST_JSP);

        assertThat(page).contains("/admin/ManageSites?method=edit&siteId=${carlos:forHtmlAttribute(site.siteId)}");
        assertThat(page).doesNotContain("ManageSites?method=update");
    }
}
