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
package io.github.carlos_emr.carlos.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import io.github.carlos_emr.carlos.commn.model.FlowSheetUserCreated;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Side effects of the a08586ca65 namespace rename, which swapped whole words ("provider" → "providers",
 * "rx" → "prescript", "common" → "commons", "security" → "sec") in values, ids, links and text that other
 * code, stored data or users still read with the old word. Each test pins one fix.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@DisplayName("a08586ca65 rename side effects")
class RenameSideEffectsRegressionTest {

    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should treat both stored spellings as the provider flowsheet scope, and send the pre-rename one")
    void shouldAcceptBothSpellings_forProviderFlowsheetScope() throws IOException {
        assertThat(FlowSheetUserCreated.SCOPE_PROVIDER).isEqualTo("provider");
        assertThat(FlowSheetUserCreated.isProviderScope("provider")).isTrue();
        assertThat(FlowSheetUserCreated.isProviderScope("providers")).isTrue();
        assertThat(FlowSheetUserCreated.isProviderScope("clinic")).isFalse();
        assertThat(FlowSheetUserCreated.isProviderScope(null)).isFalse();
        String add = read(JSP.resolve("encounter/oscarMeasurements/adminFlowsheet/FlowsheetAdd.jsp"));
        // The page shows the provider field for the option value it sends, and preselects it for either spelling.
        assertThat(add).contains("if (scopeVal == \"provider\")").contains("FlowSheetUserCreated.isProviderScope(scope)");
        assertThat(read(JSP.resolve("encounter/oscarMeasurements/adminFlowsheet/FlowsheetManager.jsp")))
                .contains("listFlowsheets('provider')");
    }

    @Test
    @DisplayName("should keep a legacy immunization schedule's provider, saved under either attribute")
    void shouldReadBothProviderAttributes_inImmunizationSchedule() throws IOException {
        String reader = "String provider = cell.getAttribute(\"providers\").isEmpty() ? cell.getAttribute(\"provider\") : cell.getAttribute(\"providers\");";
        assertThat(read(JSP.resolve("encounter/immunization/Schedule.jsp"))).contains(reader);
        assertThat(read(JSP.resolve("encounter/immunization/config/ImmunizationSetDisplay.jsp"))).contains(reader);
        String writer = read(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/encounter/immunization/pageUtil/EctImmSaveSchedule2Action.java"));
        assertThat(writer).contains("cell.setAttribute(\"provider\", provider);").contains("cell.removeAttribute(\"providers\");")
                .doesNotContain("cell.setAttribute(\"providers\"");
    }

    @Test
    @DisplayName("should link the encounter's OntarioMD disease list to its action")
    void shouldLinkOntarioMdDiseaseList_toItsAction() throws IOException {
        String header = read(JSP.resolve("casemgmt/newEncounterHeader.jsp"));
        assertThat(header).contains("${carlos:forJavaScriptAttribute(ctx)}/common/omdDiseaseList?sex=").doesNotContain("/commons/omdDiseaseList");
        assertThat(read(Path.of("src/main/webapp/WEB-INF/classes/struts-integration.xml")))
                .contains("<action name=\"common/omdDiseaseList\"");
    }

    @Test
    @DisplayName("should look up page elements by the ids the pages give them")
    void shouldUseElementIds_thatThePagesDefine() throws IOException {
        String billing = read(JSP.resolve("billing/CA/BC/billingPreferences.jsp"));
        assertThat(billing).contains("<div id=\"default-provider-alert\"").contains("getElementById(\"default-provider-alert\")")
                .doesNotContain("default-providers-alert");
        String prefs = read(JSP.resolve("provider/UserPreferences.jsp"));
        assertThat(prefs).contains("<div id=\"rx\" class=\"pref_pane\">").contains("jQuery(\"#rx\").hide();")
                .doesNotContain("#prescript");
    }

    @Test
    @DisplayName("should not show users the renamed word in alerts, errors and placeholders")
    void shouldNotShowRenamedWord_inUserText() throws IOException {
        List<Path> files = List.of(
                JSP.resolve("billing/CA/BC/billingSim.jsp"), JSP.resolve("billing/CA/ON/billingOHIPsimulation.jsp"),
                JSP.resolve("billing/CA/ON/billingON.jsp"), JSP.resolve("billing/CA/ON/billingShortcutPg1.jsp"),
                JSP.resolve("billing/CA/ON/billingONStatus.jsp"), JSP.resolve("admin/admindisplaymygroup.jsp"),
                JSP.resolve("admin/providerRole.jsp"), JSP.resolve("oscarMDS/SelectProviderAltView.jsp"),
                JSP.resolve("prevention/review.jsp"),
                Path.of("src/main/java/io/github/carlos_emr/carlos/billings/ca/on/assembler/BillingOhipSimulationViewModelAssembler.java"),
                Path.of("src/main/java/io/github/carlos_emr/carlos/billings/ca/on/service/OhipReportGenerationService.java"),
                Path.of("src/main/java/io/github/carlos_emr/carlos/webserv/rest/ProgramService.java"));
        for (Path file : files) {
            assertThat(read(file)).as(file.toString())
                    .doesNotContain("a providers").doesNotContain("one providers").doesNotContain("providers(s)")
                    .doesNotContain("providers's").doesNotContain("select providers---").doesNotContain("in providers record")
                    .doesNotContain("this providers");
        }
    }

    @Test
    @DisplayName("should tell indicator template authors the parameter name the templates define")
    void shouldNameIndicatorParameter_asTemplatesDefineIt() throws IOException {
        try (Stream<Path> templates = Files.list(Path.of("src/main/resources/indicatorXMLTemplates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".xml")).toList()) {
                assertThat(read(template)).as(template.toString()).doesNotContain("${providers}");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "es", "fr", "pl", "pt_BR"})
    @DisplayName("should label the BCAR 2020 housing and food security toggle as such in each bundle")
    void shouldLabelHousingFoodSecurity_inEveryBundle(String locale) throws IOException {
        Properties bundle = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            bundle.load(in);
        }
        String label = bundle.getProperty("form.formBCAR2020pg1.toggle.lifestyleHousing");
        // The renamed "sec" had been copied as "sector", "sek" and "segundo" (second).
        assertThat(label).isNotBlank().doesNotEndWith(" sec").doesNotContain("Sector").doesNotContain(" sek")
                .doesNotContain("segundo");
        assertThat(label.toLowerCase(java.util.Locale.ROOT))
                .containsAnyOf("security", "seguridad", "sécurité", "bezpieczeństwo", "segurança");
    }
}
