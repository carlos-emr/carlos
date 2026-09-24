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

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.Filters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the OntarioMD diabetes neurological-exam split (issue #3892, OMD DE16.066) across every
 * diabetes flowsheet variant CARLOS ships.
 *
 * <p>The 10g monofilament exam (FTLS) and the 128Hz tuning-fork exam at D1 (NRTF) are separate
 * findings. Each diabetes flowsheet must show both items with the conformance labels and must
 * carry a {@code <measurement>} definition for NRTF, because the startup measurement import
 * creates any missing type from that definition. The Flyway migration that seeds NRTF must use
 * the same display name, otherwise the flowsheet and the measurement dropdowns would disagree.</p>
 *
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
class DiabetesFlowsheetNeurologicalExamUnitTest {

    private static final String FLOWSHEET_DIR = "/oscar/encounter/oscarMeasurements/flowsheets/";
    private static final String NRTF_LABEL = "Neurological exam: 128Hz tuning fork D1";
    private static final String FTLS_LABEL = "Neurological exam: 10g monofilament";
    private static final Path NRTF_MIGRATION = Path.of("database", "mysql", "migration", "common",
            "V1.0.30__add_nrtf_tuning_fork_measurement_type.sql");

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"omdDiabetesFlowsheet.xml", "diabetesQueensFlowsheet.xml", "diabetesFlowsheet.xml"})
    @DisplayName("should label FTLS as the 10g monofilament exam without mentioning the tuning fork")
    void shouldRelabelMonofilamentItem_forEveryDiabetesFlowsheet(String resource) throws Exception {
        Element ftls = findItem(parse(resource), "FTLS").orElseThrow();

        assertThat(ftls.getAttributeValue("display_name")).isEqualTo(FTLS_LABEL);
        assertThat(ftls.getAttributeValue("guideline")).doesNotContainIgnoringCase("tuning fork");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"omdDiabetesFlowsheet.xml", "diabetesQueensFlowsheet.xml", "diabetesFlowsheet.xml"})
    @DisplayName("should add the NRTF tuning fork item and its measurement definition")
    void shouldDefineTuningForkItem_forEveryDiabetesFlowsheet(String resource) throws Exception {
        Element root = parse(resource);

        Element item = findItem(root, "NRTF").orElseThrow();
        assertThat(item.getAttributeValue("display_name")).isEqualTo(NRTF_LABEL);
        assertThat(item.getAttributeValue("value_name")).isEqualTo("Normal");

        Element definition = root.getChildren("measurement").stream()
                .filter(m -> "NRTF".equals(m.getAttributeValue("type")))
                .findFirst()
                .orElseThrow();
        assertThat(definition.getAttributeValue("typeDisplayName")).isEqualTo(NRTF_LABEL);
        assertThat(definition.getChild("validationRule")).isNotNull();
    }

    @Test
    @DisplayName("should warn in the OMD flowsheet when the tuning fork exam is overdue or never done")
    void shouldCarryOverdueRules_forOmdTuningForkItem() throws Exception {
        Element item = findItem(parse("omdDiabetesFlowsheet.xml"), "NRTF").orElseThrow();

        List<String> conditionParams = item.getChild("rules").getChildren("recommendation").stream()
                .map(rec -> rec.getChild("condition"))
                .map(cond -> cond.getAttributeValue("param") + ":" + cond.getAttributeValue("value"))
                .toList();

        assertThat(conditionParams).containsExactlyInAnyOrder("NRTF:>12", "NRTF:-1");
    }

    @Test
    @DisplayName("should seed NRTF idempotently with the flowsheet display name")
    void shouldSeedMatchingMeasurementType_forNrtfMigration() throws Exception {
        // Statements only: the header comment explains why ON DUPLICATE KEY cannot work here.
        String sql = Files.readAllLines(NRTF_MIGRATION, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));

        assertThat(sql).contains("'" + NRTF_LABEL + "'");
        // measurementType has no unique key on `type`, so the insert must be existence-guarded.
        assertThat(sql).contains("WHERE NOT EXISTS (SELECT 1 FROM `measurementType` WHERE `type` = 'NRTF')");
        assertThat(sql).doesNotContainIgnoringCase("ON DUPLICATE KEY");
    }

    private static Element parse(String resource) throws Exception {
        try (InputStream in = DiabetesFlowsheetNeurologicalExamUnitTest.class
                .getResourceAsStream(FLOWSHEET_DIR + resource)) {
            assertThat(in).as(resource + " must be on the classpath").isNotNull();
            Document document = XmlUtils.createSecureSAXBuilder().build(in);
            return document.getRootElement();
        }
    }

    /** Items sit directly under the root or inside {@code <header>} groups, depending on the variant. */
    private static Optional<Element> findItem(Element root, String measurementType) {
        for (Element item : root.getDescendants(Filters.element("item"))) {
            if (measurementType.equals(item.getAttributeValue("measurement_type"))) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
