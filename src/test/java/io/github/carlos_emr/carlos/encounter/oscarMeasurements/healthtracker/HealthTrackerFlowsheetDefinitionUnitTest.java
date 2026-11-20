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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker;

import io.github.carlos_emr.carlos.drools.DroolsHelper;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the two files that make the Health Tracker reachable at all:
 * {@code healthTracker.xml} (the backing flowsheet) and {@code tracker.drl}
 * (the flowsheet-level rule base it names).
 *
 * <p>Both failure modes these tests catch are silent at runtime.
 * {@code MeasurementFlowSheet.loadRuleBase} logs and swallows a DRL that will
 * not compile, and a flowsheet whose {@code name} drifts from {@code "tracker"}
 * simply stops being found by the nav entry and the save endpoint — neither
 * throws, the feature just quietly does nothing.
 */
@Tag("unit")
@Tag("fast")
@Tag("drools")
@Tag("measurement")
class HealthTrackerFlowsheetDefinitionUnitTest {

    private static final String FLOWSHEET_RESOURCE =
            "/oscar/encounter/oscarMeasurements/flowsheets/healthTracker.xml";
    private static final String RULES_RESOURCE =
            "/oscar/encounter/oscarMeasurements/flowsheets/tracker.drl";

    @Test
    @DisplayName("should ship the health tracker flowsheet on the classpath")
    void shouldBeOnClasspath_forFlowsheetDefinition() {
        assertThat(getClass().getResource(FLOWSHEET_RESOURCE))
                .as("healthTracker.xml must stay on the classpath; applicationContext.xml lists it")
                .isNotNull();
    }

    @Test
    @DisplayName("should declare the tracker name and display name the rest of the feature keys off")
    void shouldDeclareTrackerIdentity_forFlowsheetDefinition() throws Exception {
        Element root = parseFlowsheet();

        assertThat(root.getName()).isEqualTo("flowsheet");
        assertThat(root.getAttributeValue("name")).isEqualTo("tracker");
        assertThat(root.getAttributeValue("display_name")).isEqualTo("Health Tracker");
    }

    @Test
    @DisplayName("should stay out of the universal and dx-triggered flowsheet lists")
    void shouldDeclareNoTriggers_forFlowsheetDefinition() throws Exception {
        Element root = parseFlowsheet();

        // With any of these set, MeasurementTemplateFlowSheetConfig would register
        // "tracker" as a regular flowsheet and it would appear a second time in the
        // encounter left nav, next to its own Health Tracker entry.
        assertThat(root.getAttributeValue("is_universal")).isNull();
        assertThat(root.getAttributeValue("dxcode_triggers")).isNull();
        assertThat(root.getAttributeValue("program_triggers")).isNull();
    }

    @Test
    @DisplayName("should define no measurements so the first-run empty state is reachable")
    void shouldDefineNoItems_forFlowsheetDefinition() throws Exception {
        Element root = parseFlowsheet();

        assertThat(root.getChildren("header")).isEmpty();
        assertThat(root.getChildren("item")).isEmpty();
    }

    @Test
    @DisplayName("should point at a rule file that exists")
    void shouldReferenceAnExistingRuleFile_forFlowsheetDefinition() throws Exception {
        String dsRules = parseFlowsheet().getAttributeValue("ds_rules");

        assertThat(dsRules).isEqualTo("tracker.drl");
        assertThat(getClass().getResource(RULES_RESOURCE)).isNotNull();
    }

    @Test
    @DisplayName("should compile the tracker rule file under the current Drools runtime")
    void shouldCompile_forTrackerRuleFile() throws Exception {
        URL url = getClass().getResource(RULES_RESOURCE);
        assertThat(url).isNotNull();

        KieBase kieBase = DroolsHelper.loadFromUrl(url);

        assertThat(kieBase).isNotNull();
        assertThat(kieBase.getKiePackages()).isNotNull();
    }

    @Test
    @DisplayName("should be native DRL, not the legacy XML rule-set syntax")
    void shouldUseNativeDrlSyntax_forTrackerRuleFile() throws Exception {
        String drl = readResource(RULES_RESOURCE);

        assertThat(drl).contains("package TrackerFlowSheet;");
        assertThat(drl).doesNotContain("<rule-set");
    }

    private Element parseFlowsheet() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(FLOWSHEET_RESOURCE)) {
            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(in);
            return document.getRootElement();
        }
    }

    private String readResource(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
