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
 * existing AACP row, and the Add Measurement page that must keep legacy Yes/No readings visible.
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
                .contains("if (MeasurementDropdownOptions.isLegacyValue(opts, val)) { %>")
                .contains("<option value=\"<carlos:encode value='<%= val %>' context=\"htmlAttribute\"/>\""
                        + " selected disabled><carlos:encode value='<%= val %>' context=\"html\"/>");
    }
}
