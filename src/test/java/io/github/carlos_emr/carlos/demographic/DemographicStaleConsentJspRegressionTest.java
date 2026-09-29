/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level regressions for the chart's stale-consent guard.
 *
 * <p>Every chart save re-posts the consent choice the page showed. The page therefore also posts
 * which consent record it showed, so the save can refuse a choice made against a record a
 * colleague has since changed, and the chart page then says that the consent part was not saved.
 * These tests pin the page side of that contract: the hidden inputs, and a warning rendered only
 * from names looked up server-side.</p>
 *
 * @since 2026-09-29
 */
@DisplayName("Demographic stale consent JSP regression tests")
@Tag("unit")
@Tag("demographic")
@Tag("consent")
class DemographicStaleConsentJspRegressionTest {

    private static final int MAX_PARENT_SEARCH_DEPTH = 5;
    private static final Path CLINICAL_FORM_JSP = resolveProjectPath(Path.of(
            "src/main/webapp/WEB-INF/jsp/demographic/edit-form-clinical.jsp"));
    private static final Path EDIT_JSP = resolveProjectPath(Path.of(
            "src/main/webapp/WEB-INF/jsp/demographic/edit.jsp"));
    private static final Path UPDATE_RESULT_JSP = resolveProjectPath(Path.of(
            "src/main/webapp/WEB-INF/jsp/demographic/demographicupdatearecord.jsp"));
    private static final String MESSAGE_KEY = "demographic.demographiceditdemographic.msgConsentNotSaved";
    private static final String REVIEW_KEY = "demographic.demographiceditdemographic.msgConsentNotSavedReview";

    @Test
    @DisplayName("should post the id of the shown consent record for each consent type")
    void shouldPostShownRecordId_forEachConsentType() throws Exception {
        String consentSection = consentSection();

        assertThat(consentSection)
                .contains("name=\"consentShownId_${carlos:forHtmlAttribute(consentType.type)}\"")
                .contains("id=\"consentShownId_${carlos:forHtmlAttribute(consentType.type)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(empty patientConsent ? '' : patientConsent.id)}\"");
    }

    @Test
    @DisplayName("should post the choice of the shown consent record for each consent type")
    void shouldPostShownRecordChoice_forEachConsentType() throws Exception {
        String consentSection = consentSection();

        assertThat(consentSection)
                .contains("name=\"consentShownChoice_${carlos:forHtmlAttribute(consentType.type)}\"")
                .contains("id=\"consentShownChoice_${carlos:forHtmlAttribute(consentType.type)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(empty patientConsent ? '' : "
                        + "(patientConsent.optout ? '1' : '0'))}\"");
    }

    @Test
    @DisplayName("should not write the consent type into a shown-record input unencoded")
    void shouldEncodeConsentType_inShownRecordInputs() throws Exception {
        assertThat(consentSection())
                .doesNotContain("consentShownId_${consentType.type}")
                .doesNotContain("consentShownId_${ consentType.type }")
                .doesNotContain("consentShownChoice_${consentType.type}")
                .doesNotContain("consentShownChoice_${ consentType.type }");
    }

    @Test
    @DisplayName("should render the warning only from the names the action looked up")
    void shouldRenderWarning_fromServerSideNamesOnly() throws Exception {
        String jsp = Files.readString(EDIT_JSP, StandardCharsets.UTF_8);
        int start = jsp.indexOf("<c:if test=\"${ not empty requestScope.consentNotSavedNames }\">");
        assertThat(start).as("warning is conditional on the looked-up names").isGreaterThanOrEqualTo(0);
        String warning = jsp.substring(start, jsp.indexOf("</c:if>", start));

        assertThat(warning)
                .contains("id=\"consentNotSavedWarning\"")
                .contains("role=\"alert\"")
                .contains("<fmt:message key=\"" + MESSAGE_KEY + "\"/>")
                .contains("<fmt:message key=\"" + REVIEW_KEY + "\"/>")
                .contains("<c:forEach items=\"${ requestScope.consentNotSavedNames }\" var=\"consentNotSavedName\">")
                .contains("<carlos:encode value=\"${ consentNotSavedName }\"/>");
        // The only expression written out is the encoded name.
        assertThat(warning.replace("<carlos:encode value=\"${ consentNotSavedName }\"/>", "")
                .replace("items=\"${ requestScope.consentNotSavedNames }\"", "")
                .replace("test=\"${ not empty requestScope.consentNotSavedNames }\"", ""))
                .doesNotContain("${")
                .doesNotContain("<%=");
    }

    @Test
    @DisplayName("should never echo the raw consentNotSaved parameter on the chart page")
    void shouldNotEchoRawParameter_onChartPage() throws Exception {
        for (Path page : List.of(EDIT_JSP, CLINICAL_FORM_JSP)) {
            assertThat(Files.readString(page, StandardCharsets.UTF_8))
                    .as(page.getFileName().toString())
                    .doesNotContain("param.consentNotSaved")
                    .doesNotContain("getParameter(\"consentNotSaved\")");
        }
    }

    @Test
    @DisplayName("should hand the refused consent types on through the waiting-list step, encoded")
    void shouldCarryRefusedConsentTypes_throughWaitingListStep() throws Exception {
        assertThat(Files.readString(UPDATE_RESULT_JSP, StandardCharsets.UTF_8))
                .contains("<input type=\"hidden\" name=\"consentNotSaved\" "
                        + "value=\"${carlos:forHtmlAttribute(requestScope.consentNotSaved)}\"/>");
    }

    @Test
    @DisplayName("should define the warning text, in English, in every locale")
    void shouldDefineWarningText_inEveryLocale() throws Exception {
        String english = null;
        String englishReview = null;
        for (String locale : List.of("en", "es", "fr", "pl", "pt_BR")) {
            Properties bundle = new Properties();
            try (InputStream in = Files.newInputStream(resolveProjectPath(Path.of(
                    "src/main/resources/oscarResources_" + locale + ".properties")))) {
                bundle.load(in);
            }
            String message = bundle.getProperty(MESSAGE_KEY);
            String review = bundle.getProperty(REVIEW_KEY);
            assertThat(message).as(locale).isNotBlank();
            assertThat(review).as(locale).isNotBlank();
            if (english == null) {
                english = message;
                englishReview = review;
            }
            assertThat(message).as(locale).isEqualTo(english);
            assertThat(review).as(locale).isEqualTo(englishReview);
        }
        assertThat(english).contains("rest of the chart was saved").contains("not saved");
        assertThat(englishReview).contains("changed by someone else after this page was opened")
                .contains("enter the change again");
    }

    /** The per-consent-type loop of the clinical fragment. */
    private static String consentSection() throws Exception {
        String jsp = Files.readString(CLINICAL_FORM_JSP, StandardCharsets.UTF_8);
        int start = jsp.indexOf("<c:forEach items=\"${ consentTypes }\"");
        assertThat(start).as("consent type loop").isGreaterThanOrEqualTo(0);
        int end = jsp.indexOf("</oscar:oscarPropertiesCheck>", start);
        assertThat(end).as("end of the consent module block").isGreaterThan(start);
        return jsp.substring(start, end);
    }

    private static Path resolveProjectPath(Path relativePath) {
        try {
            Path location = Path.of(DemographicStaleConsentJspRegressionTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            Path current = Files.isRegularFile(location) ? location.getParent() : location;
            for (int depth = 0; depth <= MAX_PARENT_SEARCH_DEPTH && current != null; depth++) {
                Path candidate = current.resolve(relativePath);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
                current = current.getParent();
            }
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("Unable to resolve test class location", e);
        }
        throw new IllegalStateException("Unable to locate project file: " + relativePath);
    }
}
