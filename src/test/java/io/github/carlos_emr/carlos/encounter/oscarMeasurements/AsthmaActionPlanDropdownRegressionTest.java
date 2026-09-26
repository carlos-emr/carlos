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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDropdownOptions;
import io.github.carlos_emr.carlos.utility.XmlUtils;

import org.jdom2.Element;
import org.jdom2.filter.Filters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the wiring of the Asthma Action Plan (AACP) Provided/Revised/Reviewed dropdown
 * (issue #3893, OntarioMD DE16.098) across the flowsheet, the Flyway migration that moves the
 * existing AACP row, the Add Measurement page that must keep legacy Yes/No readings visible, and
 * the Health Tracker card that must offer the three choices instead of a free-text input.
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
@Tag("regression")
class AsthmaActionPlanDropdownRegressionTest {

    private static final String ASTHMA_FLOWSHEET =
            "/oscar/encounter/oscarMeasurements/flowsheets/omdAsthmaFlowsheet.xml";
    private static final Path AACP_MIGRATION = Path.of("database", "mysql", "migration", "common",
            "V1.0.33__aacp_provided_revised_reviewed_validation.sql");
    private static final Path ADD_MEASUREMENT_JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp",
            "encounter", "oscarMeasurements", "AddMeasurementData.jsp");
    private static final Path HEALTH_TRACKER_JSPF = Path.of("src", "main", "webapp", "WEB-INF", "jsp",
            "encounter", "oscarMeasurements", "HealthTrackerPage.jspf");

    @Test
    @DisplayName("should label the AACP value as Plan and declare the Provided/Revised/Reviewed rule")
    void shouldDeclarePlanLabelAndRule_forAacpFlowsheetItem() throws Exception {
        Element root;
        try (InputStream in = getClass().getResourceAsStream(ASTHMA_FLOWSHEET)) {
            assertThat(in).isNotNull();
            root = XmlUtils.createSecureSAXBuilder().build(in).getRootElement();
        }

        // Items may sit under <header> groups, so search the whole document.
        Element item = null;
        for (Element candidate : root.getDescendants(Filters.element("item"))) {
            if ("AACP".equals(candidate.getAttributeValue("measurement_type"))) {
                item = candidate;
                break;
            }
        }
        assertThat(item).as("AACP flowsheet item").isNotNull();
        assertThat(item.getAttributeValue("value_name")).isEqualTo("Plan");

        Element rule = root.getChildren("measurement").stream()
                .filter(e -> "AACP".equals(e.getAttributeValue("type")))
                .findFirst()
                .orElseThrow()
                .getChild("validationRule");
        assertThat(rule.getAttributeValue("name")).isEqualTo("Provided/Revised/Reviewed");
        assertThat(rule.getAttributeValue("regularExp")).isEqualTo("Provided|Revised|Reviewed");
    }

    @Test
    @DisplayName("should point AACP at the new rule deterministically and keep customized instructions")
    void shouldGuardEveryStatement_forAacpMigration() throws Exception {
        String sql = Files.readString(AACP_MIGRATION, StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("INSERT INTO `validations` (`name`, `regularExp`)")
                .contains("WHERE NOT EXISTS (")
                .contains("ORDER BY `id`")
                .contains("AND `measuringInstruction` = 'Yes/No';");
    }

    @Test
    @DisplayName("should render a legacy stored value as an encoded, selected, disabled option")
    void shouldRenderLegacyValue_asEncodedDisabledOption() throws Exception {
        String jsp = Files.readString(ADD_MEASUREMENT_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("MeasurementDropdownOptions.forValidation(validations)")
                .contains("boolean legacyValue = MeasurementDropdownOptions.isLegacyValue(opts, val);")
                .contains("if (legacyValue) { %>")
                .contains("<option value=\"<carlos:encode value='<%= val %>' context=\"htmlAttribute\"/>\""
                        + " selected disabled><carlos:encode value='<%= val %>' context=\"html\"/>");
    }

    @Test
    @DisplayName("should render a slash-named rule such as AACP as one radio per option on the tracker")
    void shouldRenderChoiceRadios_forProvidedRevisedReviewedOnHealthTracker() throws Exception {
        String jspf = Files.readString(HEALTH_TRACKER_JSPF, StandardCharsets.UTF_8);

        // The renderer derives the choices from the rule name through the same helper the Add
        // Measurement page uses, and only for rules the Yes/No and Review branches did not claim,
        // so Provided/Revised/Reviewed no longer falls through to the free-text input.
        assertThat(jspf)
                .contains("import=\"io.github.carlos_emr.carlos.encounter.oscarMeasurements.util"
                        + ".MeasurementDropdownOptions\"")
                .contains("List<String> choiceOptions = (yesNo || reviewOnly)")
                .contains(": MeasurementDropdownOptions.forValidationName(validationName);")
                .contains("boolean choiceList = !choiceOptions.isEmpty();")
                .contains("<% } else if (choiceList) { %>")
                .contains("<% for (String choice : choiceOptions) {")
                .contains("<input class=\"form-check-input entry-input\" type=\"radio\" value=\"<%= encChoice %>\"")
                .contains("name=\"<%= encField %>\" id=\"<%= choiceId %>\">")
                .contains("<label class=\"form-check-label\" for=\"<%= choiceId %>\"><carlos:encode");

        // The choice branch must sit before the free-text fallback, which stays for everything else.
        int choiceBranch = jspf.indexOf("<% } else if (choiceList) { %>");
        int freeTextBranch = jspf.indexOf("placeholder=\"Enter Data\"");
        assertThat(choiceBranch).isPositive();
        assertThat(freeTextBranch).isGreaterThan(choiceBranch);

        // And the helper does yield the three allowed AACP answers for that rule name.
        assertThat(MeasurementDropdownOptions.forValidationName("Provided/Revised/Reviewed"))
                .containsExactly("Provided", "Revised", "Reviewed");
    }

    @Test
    @DisplayName("should not submit or re-save a legacy value, and say so on the page")
    void shouldNotResubmitLegacyValue_whenShownForReference() throws Exception {
        String jsp = Files.readString(ADD_MEASUREMENT_JSP, StandardCharsets.UTF_8);

        // The legacy option is the only carrier of the value and stays disabled: no hidden
        // inputValue-* re-posts an old reading as new data. Viewing a saved reading is
        // read-only: every control is disabled and only Delete posts.
        assertThat(jsp).doesNotContain("type=\"hidden\" name=\"<%= \"inputValue-\"")
                .doesNotContain("type=\"hidden\" name=\"<%=\"inputValue-\"")
                .contains("<span class=\"legacyValueNote\" id=\"<%=\"legacyValueNote-\"+ctr%>\">"
                        + "Recorded under an earlier option list; shown for reference only and not saved again.</span>")
                .contains("saveAction = \"encounter/oscarMeasurements/DeleteData2\";")
                .contains("Array.from(f.elements).forEach(function(el) { el.disabled = true; });")
                .contains("<input type=\"submit\" name=\"delete\" value=\"Delete\" id=\"deleteButton\"/>");
    }
}
