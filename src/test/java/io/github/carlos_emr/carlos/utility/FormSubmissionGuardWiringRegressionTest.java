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
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static wiring of the issue #4225 write-integrity controls: each guarded form renders its
 * submission token, each guarded action maps the refusal page, and the Master Record edit form
 * carries its record version. A form that silently loses its hidden field still saves, but saves
 * twice again, which no unit test of the actions would notice.
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("security")
@DisplayName("Write-integrity wiring (issue #4225)")
class FormSubmissionGuardWiringRegressionTest {

    private static final Path WEBAPP = Path.of("src", "main", "webapp");
    private static final Path CLASSES = WEBAPP.resolve(Path.of("WEB-INF", "classes"));
    private static final Path JSP = WEBAPP.resolve(Path.of("WEB-INF", "jsp"));

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** The {@code <action>} element of {@code name} in a Struts module file. */
    private static String actionElement(String strutsFile, String name) throws IOException {
        Matcher matcher = Pattern.compile("<action\\s+name=\"" + Pattern.quote(name) + "\".*?</action>", Pattern.DOTALL)
                .matcher(read(CLASSES.resolve(strutsFile)));
        assertThat(matcher.find()).as("struts action %s in %s", name, strutsFile).isTrue();
        return matcher.group();
    }

    @ParameterizedTest(name = "{0} renders a {1} token")
    @CsvSource({
            "appointment/addappointment.jsp, APPOINTMENT_ADD",
            "demographic/add.jsp, DEMOGRAPHIC_ADD",
            "encounter/oscarConsultationRequest/config/AddService.jsp, CONSULTATION_SERVICE_ADD"
    })
    void shouldRenderTheSubmissionToken_inEachGuardedScriptletForm(String jsp, String scope) throws IOException {
        String page = read(JSP.resolve(jsp));

        assertThat(page).contains("name=\"<%= FormSubmissionGuard.PARAMETER %>\"");
        assertThat(page).contains("FormSubmissionGuard.issue(session, FormSubmissionGuard.Scope." + scope);
    }

    @Test
    void shouldRenderTheActionIssuedToken_inTheOntarioBillReview() throws IOException {
        String review = read(JSP.resolve(Path.of("billing", "CA", "ON", "billingONReview.jsp")));
        String action = read(Path.of("src", "main", "java", "io", "github", "carlos_emr", "carlos", "billings", "ca",
                "on", "web", "ViewBillingOnReview2Action.java"));

        assertThat(review).contains("name=\"" + FormSubmissionGuard.PARAMETER + "\" value=\"<carlos:encode value='${formSubmissionToken}'");
        assertThat(action).contains("request.setAttribute(\"formSubmissionToken\", FormSubmissionGuard.issue(");
    }

    @ParameterizedTest(name = "{1} maps the refusal page")
    @CsvSource({
            "struts-scheduling.xml, appointment/AddRecord",
            "struts-billing.xml, billing/CA/ON/BillingONSave",
            "struts-demographic.xml, demographic/DemographicAddRecord",
            "struts-encounter.xml, encounter/AddService"
    })
    void shouldMapTheRefusalPage_forEachGuardedAction(String strutsFile, String action) throws IOException {
        assertThat(actionElement(strutsFile, action)).contains("<result name=\"" + FormSubmissionGuard.REFUSED_RESULT
                + "\">/WEB-INF/jsp/common/formSubmissionRefused.jsp</result>");
        assertThat(JSP.resolve(Path.of("common", "formSubmissionRefused.jsp"))).exists();
    }

    @Test
    void shouldCarryTheRecordVersion_inTheMasterRecordEditForm() throws IOException {
        String edit = read(JSP.resolve(Path.of("demographic", "edit.jsp")));
        int form = edit.indexOf("name=\"updatedelete\"");
        int version = edit.indexOf("name=\"demographicVersion\"");

        assertThat(form).isPositive();
        assertThat(version).as("the version field is inside the Update Record form").isGreaterThan(form);
        assertThat(edit.substring(version)).contains("value=\"<carlos:encode value='${demographicEditVersion}' context=\"htmlAttribute\"/>\"");
        assertThat(actionElement("struts-demographic.xml", "demographic/DemographicUpdate"))
                .contains("<result name=\"conflict\">/WEB-INF/jsp/demographic/demographicUpdateConflict.jsp</result>");
    }

    @Test
    void shouldEncodeEveryValue_onTheConflictPage() throws IOException {
        String conflict = read(JSP.resolve(Path.of("demographic", "demographicUpdateConflict.jsp")));

        // Every request-derived value goes through the CARLOS null-safe encoder for its context.
        Matcher value = Pattern.compile("\\$\\{(entry\\[[01]]|difference\\.(yours|current|name)|conflictVersion|demographicNo)")
                .matcher(conflict);
        int values = 0;
        while (value.find()) {
            values++;
            String before = conflict.substring(Math.max(0, value.start() - 30), value.start());
            assertThat(before).as("the value %s is rendered through a carlos encoder", value.group()).contains("carlos:");
        }
        assertThat(values).isGreaterThanOrEqualTo(6);
        assertThat(conflict).doesNotContain("<%=");
        assertThat(conflict).contains("<carlos:encode value='${entry[0]}' context='htmlAttribute'/>",
                "<carlos:encode value='${entry[1]}' context='htmlAttribute'/>",
                "<carlos:encode value=\"${difference.yours}\"/>", "<carlos:encode value=\"${difference.current}\"/>");
    }
}
