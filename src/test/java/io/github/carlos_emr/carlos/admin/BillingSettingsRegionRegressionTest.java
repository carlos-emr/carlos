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
package io.github.carlos_emr.carlos.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regressions for the Billing Settings admin page (issue #3359).
 *
 * <p>The page shipped an OSCAR-era heading and, for an Ontario clinic, a single unlabelled row
 * reading "No billing options to display." next to a Save button that posted a form with no
 * inputs — which overwrote the stored billing properties and system preferences with null.
 * These tests pin the CARLOS branding, the Ontario explanation, and the BC-only gating of both
 * the settings inputs and the save path.</p>
 *
 * @since 2026-09-18
 */
@DisplayName("billing settings admin page region regressions")
@Tag("unit")
@Tag("admin")
class BillingSettingsRegionRegressionTest {

    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path BILLING_SETTINGS_JSP =
            resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp/admin/billingSettings.jsp"));
    private static final Path RESOURCES_DIRECTORY = resolveProjectPath(Path.of("src/main/resources"));
    private static final String[] LOCALES = {"en", "es", "fr", "pl", "pt_BR"};

    private static final String BC_BRANCH = "<% if (hasEditableSettings) { %>";
    private static final String ONTARIO_BRANCH = "<% } else if (isOntarioBillRegion) { %>";
    private static final String OTHER_REGION_BRANCH = "<% } else { %>";

    /**
     * Written once so the "English bundle says exactly this" assertion and the "no other bundle
     * copies the English sentence" assertion can never drift apart.
     */
    private static final String ENGLISH_HEADING =
            "admin.billingSettings.heading=Manage CARLOS Billing Settings";
    private static final String ENGLISH_ON_NO_OPTIONS =
            "admin.billingSettings.onNoOptions=Ontario (OHIP) billing has no clinic-wide settings on this page.";

    @Test
    @DisplayName("heading should carry CARLOS branding in every supported locale")
    void shouldBrandHeadingAsCarlos_forAllSupportedLocales() throws IOException {
        for (String locale : LOCALES) {
            String resources = readBundle(locale);
            String heading = readProperty(resources, "admin.billingSettings.heading");

            assertThat(heading)
                    .as("billing settings heading for %s", locale)
                    .isNotEmpty()
                    .doesNotContain("OSCAR")
                    .contains("CARLOS");
        }

        assertThat(readBundle("en")).contains(ENGLISH_HEADING);
    }

    @Test
    @DisplayName("Ontario should get an explanation rather than an unlabelled empty row")
    void shouldExplainOntarioHasNoPageSettings_whenBillRegionIsOntario() throws IOException {
        String jsp = readJsp();

        int ontarioBranch = jsp.indexOf(ONTARIO_BRANCH);
        int otherRegionBranch = jsp.indexOf(OTHER_REGION_BRANCH, ontarioBranch);
        int ontarioMessage = jsp.indexOf("<fmt:message key=\"admin.billingSettings.onNoOptions\"/>");

        assertThat(ontarioBranch).as("Ontario branch").isGreaterThanOrEqualTo(0);
        assertThat(otherRegionBranch).as("fallback branch for other regions").isGreaterThan(ontarioBranch);
        assertThat(ontarioMessage)
                .as("the Ontario explanation must render only in the Ontario branch")
                .isBetween(ontarioBranch, otherRegionBranch);

        // The generic "No billing options to display." row stays for a region that is neither
        // BC nor ON, so it must not be what an Ontario clinic sees.
        assertThat(jsp.indexOf("<fmt:message key=\"admin.billingSettings.noOptions\"/>"))
                .as("the generic empty row belongs to the fallback branch")
                .isGreaterThan(otherRegionBranch);
    }

    @Test
    @DisplayName("settings inputs should render only for the BC billing region")
    void shouldRenderSettingsInputs_whenBillRegionIsBritishColumbia() throws IOException {
        String jsp = readJsp();

        int bcBranch = jsp.indexOf(BC_BRANCH);
        int ontarioBranch = jsp.indexOf(ONTARIO_BRANCH);

        assertThat(bcBranch).as("BC branch").isGreaterThanOrEqualTo(0);
        assertThat(ontarioBranch).as("Ontario branch closing the BC branch").isGreaterThan(bcBranch);

        for (String input : new String[] {
                "name=\"auto_populate_refer\"",
                "name=\"bc_default_service_location\"",
                "name=\"default_billing_form\"",
                "name=\"invoice_use_custom_clinic_info\"",
                "name=\"invoice_custom_clinic_info\""}) {
            assertThat(jsp.indexOf(input))
                    .as("%s must render only for BC", input)
                    .isBetween(bcBranch, ontarioBranch);
        }
    }

    @Test
    @DisplayName("Save control should be absent for a region with nothing to save")
    void shouldHideSaveControl_whenRegionHasNoEditableSettings() throws IOException {
        String jsp = readJsp();

        int saveGate = jsp.lastIndexOf(BC_BRANCH);
        int saveButton = jsp.indexOf("name=\"saveBillingSettings\"");

        assertThat(saveGate)
                .as("the Save control must sit behind its own editable-settings gate")
                .isGreaterThan(jsp.indexOf(ONTARIO_BRANCH));
        assertThat(saveButton).as("Save button").isGreaterThan(saveGate);
    }

    @Test
    @DisplayName("save path should not run for a region that never rendered the inputs")
    void shouldSkipSave_whenRegionHasNoEditableSettings() throws IOException {
        String jsp = readJsp();

        assertThat(jsp).contains("boolean hasEditableSettings = \"BC\".equals(billRegion);");
        assertThat(jsp).contains("boolean isOntarioBillRegion = \"ON\".equals(billRegion);");
        // Without this gate every request.getParameter(...) below reads null for a non-BC region
        // and the save loop persists null over the stored Property / SystemPreferences values.
        assertThat(jsp)
                .containsPattern("(?s)if \\(hasEditableSettings\\s*&&\\s*request\\.getParameter\\(\"dboperation\"\\)");
    }

    @Test
    @DisplayName("Ontario explanation should be translated in every supported locale")
    void shouldTranslateOntarioExplanation_forAllSupportedLocales() throws IOException {
        String englishValue = ENGLISH_ON_NO_OPTIONS.substring(ENGLISH_ON_NO_OPTIONS.indexOf('=') + 1);

        for (String locale : LOCALES) {
            String resources = readBundle(locale);

            // Key presence is not enough: "key=" satisfies a contains() check while
            // <fmt:message> renders nothing, so require a real value.
            assertThat(resources)
                    .as("Ontario billing settings explanation for %s", locale)
                    .containsPattern("(?m)^[ \\t]*admin\\.billingSettings\\.onNoOptions[ \\t]*[=:][ \\t]*\\S");

            if (!"en".equals(locale)) {
                assertThat(resources)
                        .as("the %s bundle must not fall back to the English sentence", locale)
                        .doesNotContain(englishValue);
            }
        }

        assertThat(readBundle("en")).contains(ENGLISH_ON_NO_OPTIONS);
    }

    private String readJsp() throws IOException {
        return Files.readString(BILLING_SETTINGS_JSP, StandardCharsets.UTF_8);
    }

    private String readBundle(String locale) throws IOException {
        return Files.readString(
                RESOURCES_DIRECTORY.resolve("oscarResources_" + locale + ".properties"),
                StandardCharsets.UTF_8);
    }

    /**
     * Reads a single property value from raw bundle text. The bundles are ASCII with
     * {@code \\uXXXX} escapes, so the raw value is compared as written on disk.
     */
    private String readProperty(String bundle, String key) {
        for (String line : bundle.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + "=")) {
                return trimmed.substring(key.length() + 1).trim();
            }
        }
        return "";
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath + " from "
                + System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")));
    }
}
