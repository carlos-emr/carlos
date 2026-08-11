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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression tests for export template validation on the Demographic Export screen.
 *
 * <p>Covers GitHub issue #3405: the E2E template was offered by the JSP but had no
 * implementation, so submitting it fell through the export switch and produced the generic
 * "export failed" UI. The JSP must offer only templates the action implements, and any other
 * template value must be refused with an explicit validation error.</p>
 *
 * @since 2026-08-11
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicExportAction42Action export template validation")
class DemographicExportAction42ActionTemplateValidationTest extends DemographicExportActionUnitTestBase {

    /** Bounded walk up from the working directory, so the test also runs from an IDE fork. */
    private static final int MAX_PARENT_SEARCH_DEPTH = 4;

    private static final Path EXPORT_JSP = resolveProjectPath(
            Path.of("src/main/webapp/WEB-INF/jsp/demographic/demographicExport.jsp"));

    private static final Path ENGLISH_RESOURCES = resolveProjectPath(
            Path.of("src/main/resources/oscarResources_en.properties"));

    /** Resource key holding the localized text for {@code UNSUPPORTED_TEMPLATE_CODE}. */
    private static final String UNSUPPORTED_TEMPLATE_KEY =
            "demographic.demographicexport.unsupportedTemplate";

    /** Matches the template picker rendered by the export JSP. */
    private static final Pattern TEMPLATE_PICKER =
            Pattern.compile("<select[^>]*name=\"template\"[^>]*>(.*?)</select>", Pattern.DOTALL);

    private static final Pattern OPTION_VALUE = Pattern.compile("<option[^>]*value=\"([^\"]*)\"");

    /** Matches a template constant referenced from a JSP expression, e.g. {@code CMS4}. */
    private static final Pattern TEMPLATE_CONSTANT =
            Pattern.compile("DemographicExportAction42Action\\.(\\w+)");

    /**
     * A POST carrying a template the action does not implement must be refused explicitly:
     * HTTP 400 plus a reason code header, with no export work attempted.
     */
    @Nested
    @DisplayName("Unsupported template rejection")
    class UnsupportedTemplateRejection {

        @Test
        @DisplayName("should reject the retired E2E template with a validation error")
        void shouldRejectExport_whenTemplateIsE2E() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            action.setDemographicNo("123");
            action.setTemplate(String.valueOf(DemographicExportAction42Action.E2E));

            String result = action.execute();

            // NONE, not "fail": a "fail" result forwards the export page into a 400 response, and
            // ResponseSanitizationFilter cannot replay a captured 4xx body of that size, which
            // surfaced live as a 500 error page instead of the validation error.
            assertThat(result).isEqualTo(ActionSupport.NONE);
            verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
            verify(response).setHeader("X-Export-Status", "error");
            verify(response).setHeader("X-Export-Error",
                    DemographicExportAction42Action.UNSUPPORTED_TEMPLATE_CODE);
            verify(response, never()).getOutputStream();
            verifyNoInteractions(demographicExtDao);
        }

        @Test
        @DisplayName("should reject a template value that is not an integer")
        void shouldRejectExport_whenTemplateIsNotNumeric() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            action.setDemographicNo("123");
            action.setTemplate("not-a-template");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
            verify(response).setHeader("X-Export-Status", "error");
            verify(response).setHeader("X-Export-Error",
                    DemographicExportAction42Action.UNSUPPORTED_TEMPLATE_CODE);
            // An unparseable value must not quietly run the CMS4 export instead.
            verifyNoInteractions(demographicExtDao);
        }

        /**
         * Pins the export switch to {@code SUPPORTED_TEMPLATES}: every value outside that set has
         * to be refused, so a new {@code case} cannot be added without also declaring it supported.
         */
        @ParameterizedTest(name = "template={0}")
        @ValueSource(ints = {-2, -1, 1, 2, 3, 42})
        @DisplayName("should reject every template value outside the supported set")
        void shouldRejectExport_whenTemplateIsNotSupported(int template) throws Exception {
            assertThat(DemographicExportAction42Action.SUPPORTED_TEMPLATES).doesNotContain(template);
            when(request.getMethod()).thenReturn("POST");
            action.setDemographicNo("123");
            action.setTemplate(String.valueOf(template));

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
            verifyNoInteractions(demographicExtDao);
        }

        @Test
        @DisplayName("should still audit the attempt when the template is rejected")
        void shouldAuditExportAttempt_whenTemplateIsRejected() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            action.setDemographicNo("123");
            action.setTemplate(String.valueOf(DemographicExportAction42Action.E2E));

            action.execute();

            ArgumentCaptor<OscarLog> auditLogCaptor = ArgumentCaptor.forClass(OscarLog.class);
            logActionMock.verify(() -> LogAction.addLogSynchronous(auditLogCaptor.capture()));
            OscarLog auditLog = auditLogCaptor.getValue();
            assertThat(auditLog.getAction()).isEqualTo(LogConst.EXPORT);
            assertThat(auditLog.getContent()).isEqualTo(LogConst.CON_DEMOGRAPHIC);
            assertThat(auditLog.getData())
                    .contains("Exported 0 records", "outcome=fail",
                            "ids=" + DemographicExportAction42Action.NO_IDS_RESOLVED)
                    // The request is refused before any patient is resolved, so the audit record
                    // must not name the demographic that was asked for.
                    .doesNotContain("123");
        }
    }

    /**
     * Ties the templates the JSP renders to the templates the action implements, so a future
     * option cannot be added to the picker without a supporting export path.
     */
    @Nested
    @DisplayName("JSP and action template parity")
    class TemplateParity {

        @Test
        @DisplayName("should offer only supported templates in the export picker")
        void shouldOfferOnlySupportedTemplates_inExportJsp() throws Exception {
            assertThat(offeredTemplates()).isEqualTo(DemographicExportAction42Action.SUPPORTED_TEMPLATES);
        }

        @Test
        @DisplayName("should no longer offer the unimplemented E2E template")
        void shouldNotOfferE2ETemplate_inExportJsp() throws Exception {
            assertThat(templatePickerMarkup()).doesNotContain("E2E");
            assertThat(offeredTemplates()).doesNotContain(DemographicExportAction42Action.E2E);
        }

        @Test
        @DisplayName("should keep EMR DM 5.0 as the supported export template")
        void shouldKeepCms4_asSupportedTemplate() throws Exception {
            assertThat(DemographicExportAction42Action.SUPPORTED_TEMPLATES)
                    .containsExactly(DemographicExportAction42Action.CMS4);
            assertThat(templatePickerMarkup()).contains("EMR DM 5.0");
        }
    }

    /**
     * The rejection reason travels as a code, so the page must carry localized text for it;
     * otherwise the administrator sees only the generic "export failed" message again.
     */
    @Nested
    @DisplayName("Rejection reason wiring")
    class RejectionReasonWiring {

        @Test
        @DisplayName("should render localized text for the reason code the action returns")
        void shouldRenderLocalizedReason_forUnsupportedTemplateCode() throws Exception {
            String jsp = Files.readString(EXPORT_JSP, StandardCharsets.UTF_8);

            assertThat(jsp).contains("data-export-error-code=\""
                    + DemographicExportAction42Action.UNSUPPORTED_TEMPLATE_CODE + "\"");
            assertThat(jsp).contains(UNSUPPORTED_TEMPLATE_KEY);
        }

        @Test
        @DisplayName("should define the reason message in the resource bundle")
        void shouldDefineReasonMessage_inResourceBundle() throws Exception {
            assertThat(Files.readString(ENGLISH_RESOURCES, StandardCharsets.UTF_8))
                    .contains(UNSUPPORTED_TEMPLATE_KEY + "=");
        }
    }

    /** Returns the markup inside the template picker element of the export JSP. */
    private static String templatePickerMarkup() throws IOException {
        String jsp = Files.readString(EXPORT_JSP, StandardCharsets.UTF_8);
        Matcher picker = TEMPLATE_PICKER.matcher(jsp);
        assertThat(picker.find())
                .as("template picker element of %s", EXPORT_JSP)
                .isTrue();
        return picker.group(1);
    }

    /**
     * Resolves the template values the JSP offers. Option values are JSP expressions naming a
     * constant on the action (or plain integers), so both forms are resolved to their int value.
     */
    private static Set<Integer> offeredTemplates() throws Exception {
        Set<Integer> templates = new LinkedHashSet<>();
        Matcher option = OPTION_VALUE.matcher(templatePickerMarkup());
        while (option.find()) {
            templates.add(resolveTemplateValue(option.group(1).trim()));
        }
        assertThat(templates).as("template options offered by %s", EXPORT_JSP).isNotEmpty();
        return templates;
    }

    /**
     * Resolves a repository-relative path against the working directory, walking a bounded number
     * of parents. Surefire runs with {@code basedir} as the working directory, but IDE and forked
     * runs do not always, and a missing file must fail as a clear error rather than a mystery
     * assertion.
     */
    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth <= MAX_PARENT_SEARCH_DEPTH && current != null; depth++) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate project file: " + relativePath);
    }

    private static Integer resolveTemplateValue(String optionValue) throws Exception {
        Matcher constant = TEMPLATE_CONSTANT.matcher(optionValue);
        if (constant.find()) {
            return DemographicExportAction42Action.class.getField(constant.group(1)).getInt(null);
        }
        String literal = optionValue.replace("<%=", "").replace("%>", "").trim();
        return Integer.valueOf(literal);
    }
}
