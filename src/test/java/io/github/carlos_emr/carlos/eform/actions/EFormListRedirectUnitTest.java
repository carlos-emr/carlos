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
package io.github.carlos_emr.carlos.eform.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the POST/redirect/GET fix for the patient/independent eForm delete and restore actions.
 * Before it, {@code eform/removeEForm} and {@code eform/unRemoveEForm} forwarded (keeping POST)
 * to list pages whose gate only accepts GET, so every successful delete showed
 * "CARLOS Error: 405".
 */
@Tag("unit")
@Tag("eform")
class EFormListRedirectUnitTest {

    private static final Path STRUTS_EFORM_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-eform.xml");

    @Test
    @DisplayName("should append only the named context params, URL-encoded")
    void shouldAppendNamedParams_withUrlEncoding() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("demographic_no")).thenReturn("42");
        when(request.getParameter("group_view")).thenReturn("Intake & Forms");
        when(request.getParameter("parentAjaxId")).thenReturn("eforms");
        when(request.getParameter("fdid")).thenReturn("99");

        String target = EFormListRedirect.to("/eform/efmpatientformlist", request,
                "demographic_no", "group_view", "parentAjaxId", "appointment", "orderby");

        assertThat(target).isEqualTo(
                "/eform/efmpatientformlist?demographic_no=42&group_view=Intake+%26+Forms&parentAjaxId=eforms");
    }

    @Test
    @DisplayName("should return the bare path when no context params are present")
    void shouldReturnBarePath_whenNoParamsPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("orderby")).thenReturn("");

        assertThat(EFormListRedirect.to("/eform/efmmanageindependent", request, "orderby"))
                .isEqualTo("/eform/efmmanageindependent");
    }

    @Test
    @DisplayName("should encode OGNL and URL metacharacters so request data cannot reshape the target")
    void shouldEncodeMetacharacters_forHostileValue() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("parentAjaxId")).thenReturn("${1+1}&x=/evil?y#z");

        String target = EFormListRedirect.to("/eform/efmpatientformlistdeleted", request, "parentAjaxId");

        assertThat(target)
                .startsWith("/eform/efmpatientformlistdeleted?parentAjaxId=")
                .doesNotContain("${", "&x=", "#", "?y");
    }

    @Test
    @DisplayName("should redirect, not forward, after eForm delete and restore")
    void shouldRedirect_forRemoveAndUnRemoveEForm() throws IOException {
        String xml = Files.readString(STRUTS_EFORM_XML, StandardCharsets.UTF_8);
        for (String action : new String[] {"eform/removeEForm", "eform/unRemoveEForm"}) {
            Matcher m = Pattern.compile(
                    "<action name=\"" + Pattern.quote(action) + "\"[^>]*>(.*?)</action>",
                    Pattern.DOTALL).matcher(xml);
            assertThat(m.find()).as(action + " mapping").isTrue();
            String body = m.group(1).replaceAll("(?s)<!--.*?-->", "");
            assertThat(body)
                    .as(action + " results")
                    .contains("<result name=\"success\" type=\"redirect\">${redirectTarget}</result>")
                    .doesNotContainPattern("<result name=\"[^\"]+\">/eform/");
        }
    }

    @Test
    @DisplayName("should target the groups page for the named group, keeping sort and schedule nav")
    void shouldTargetGroupPage_withGroupViewAndContext() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("orderby")).thenReturn("form_name");
        when(request.getParameter("scheduleNav")).thenReturn("1");
        when(request.getParameter("groupName")).thenReturn("ignored: not echoed by name");

        assertThat(EFormListRedirect.toGroup(request, "Intake & ${1+1}"))
                .isEqualTo("/eform/efmmanageformgroups?orderby=form_name&scheduleNav=1&group_view=Intake+%26+%24%7B1%2B1%7D");
    }

    @Test
    @DisplayName("should target the default group when the group was deleted")
    void shouldTargetDefaultGroup_whenGroupNameAbsent() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThat(EFormListRedirect.toGroup(request, null)).isEqualTo("/eform/efmmanageformgroups");
        assertThat(EFormListRedirect.toGroup(request, "")).isEqualTo("/eform/efmmanageformgroups");
    }

    @Test
    @DisplayName("should redirect, not forward, after every eForm group change")
    void shouldRedirect_forGroupActions() throws IOException {
        // Issue #4130: these forwarded (keeping POST) to the GET-only groups page, so a
        // successful add, remove or delete ended on a 405 once the CSRF token reached them.
        String xml = Files.readString(STRUTS_EFORM_XML, StandardCharsets.UTF_8);
        for (String action : new String[] {
                "eform/addGroup", "eform/addToGroup", "eforms/removeFromGroup", "eforms/delGroup"}) {
            Matcher m = Pattern.compile(
                    "<action name=\"" + Pattern.quote(action) + "\"[^>]*>(.*?)</action>",
                    Pattern.DOTALL).matcher(xml);
            assertThat(m.find()).as(action + " mapping").isTrue();
            String body = m.group(1).replaceAll("(?s)<!--.*?-->", "");
            assertThat(body)
                    .as(action + " results")
                    .contains("<result name=\"success\" type=\"redirect\">${redirectTarget}</result>")
                    .doesNotContainPattern("<result name=\"[^\"]+\">/eform/");
        }
    }
}
